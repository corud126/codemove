package com.codemove.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.code.CodeService;
import com.codemove.domain.MoveRequest;
import com.codemove.domain.Quote;
import com.codemove.domain.QuoteItem;
import com.codemove.domain.ReVideo;
import com.codemove.domain.RequestItem;
import com.codemove.member.LoginMember;
import com.codemove.notification.NotificationDetail;
import com.codemove.notification.NotificationService;
import com.codemove.quote.QuoteMapper;
import com.codemove.schedule.AvailabilityService;

import lombok.RequiredArgsConstructor;

/**
 * 견적 요청 제출: 요청 · 영상 · 품목 저장과 동시에 견적서 초안(QUOTE_STATUS 01 견적 검토)을 만든다 (ERD 0단계).
 * 부피·금액은 화면 값을 믿지 않고 ItemCatalog · PriceCalculator 로 다시 계산한다.
 * 입력이 잘못되면 화면에 보여줄 문구로 IllegalArgumentException 을 던진다.
 */
@Service
@RequiredArgsConstructor
public class MoveRequestService {

	/** TBL_QUOTE_ITEM.QUANTITY NUMBER(3) */
	private static final int MAX_QUANTITY = 999;
	private static final String SOURCE_AI = "01";
	private static final String SOURCE_USER = "02";

	private final MoveRequestMapper requestMapper;
	private final NotificationService notificationService;
	private final QuoteMapper quoteMapper;
	private final ItemCatalog catalog;
	private final PriceCalculator priceCalculator;
	private final CodeService codeService;
	private final AvailabilityService availabilityService;

	/** 저장할 품목 · 합계 · 예상 금액 */
	public record Calculation(List<RequestItem> items, int count, BigDecimal cbm, PriceCalculator.Estimate estimate) {
	}

	/** 화면 품목을 저장할 품목으로 바꾸고 합계·예상 금액을 계산 */
	public Calculation calculate(QuoteForm form, QuoteSession session) {
		if (form.items() == null || form.items().isEmpty()) {
			throw new IllegalArgumentException("품목을 하나 이상 넣어 주세요.");
		}
		List<VideoAnalyzer.Detected> detected = session != null && session.detected() != null ? session.detected() : List.of();
		List<RequestItem> items = new ArrayList<>();
		for (QuoteForm.ItemForm f : form.items()) {
			String size = codeService.codeOf("ITEM_SIZE", f.size());
			if (f.name() == null || f.name().isBlank() || f.name().trim().length() > 50 || size == null
					|| f.count() < 1 || f.count() > MAX_QUANTITY) {
				throw new IllegalArgumentException("품목 정보를 다시 확인해 주세요.");
			}
			String name = f.name().trim();
			boolean fromAi = f.aiIndex() != null && f.aiIndex() >= 0 && f.aiIndex() < detected.size();
			RequestItem item = new RequestItem();
			item.setItemName(name);
			item.setCategory(catalog.find(name).category());
			item.setItemSize(size);
			item.setQuantity(f.count());
			item.setVolume(catalog.volume(name, size, f.count()));
			item.setSource(fromAi ? SOURCE_AI : SOURCE_USER);
			item.setConfidence(fromAi ? detected.get(f.aiIndex()).confidence() : null);
			item.setNeedsReview(fromAi && f.review() ? "Y" : "N");
			items.add(item);
		}
		int count = items.stream().mapToInt(RequestItem::getQuantity).sum();
		BigDecimal cbm = items.stream().map(RequestItem::getVolume).reduce(BigDecimal.ZERO, BigDecimal::add);
		String pack = codeService.codeOf("PACK_TYPE", form.pack());
		return new Calculation(items, count, cbm, priceCalculator.estimate(cbm, pack));
	}

	/** 요청 저장 + 견적서 초안 생성. 요청 ID 를 돌려준다 */
	@Transactional
	public Long submit(LoginMember member, QuoteForm form, QuoteSession session) {
		if (session == null || session.videoPath() == null) {
			throw new IllegalArgumentException("영상을 먼저 올려 주세요.");
		}
		Calculation calc = calculate(form, session);
		String house = codeService.codeOf("HOUSE_TYPE", form.house());
		String pack = codeService.codeOf("PACK_TYPE", form.pack());
		if (house == null || pack == null) {
			throw new IllegalArgumentException("이사 정보를 다시 선택해 주세요.");
		}
		QuoteForm.Address from = checkAddress(form.from());
		QuoteForm.Address to = checkAddress(form.to());
		LocalDate date = parseDate(form.date());
		String time = date == null ? null : codeService.codeOf("PREFER_TIME", form.time());
		if (date != null && (time == null || !availabilityService.isAvailable(date))) {
			throw new IllegalArgumentException("선택한 날짜는 예약할 수 없어요. 다른 날짜를 골라 주세요.");
		}

		MoveRequest request = new MoveRequest();
		request.setMemberId(member.memberId());
		request.setHouseType(house);
		request.setPackType(pack);
		request.setMoveDate(date);
		request.setPreferTime(time);
		request.setFromAddress(from.road());
		request.setFromDetail(from.detail());
		request.setToAddress(to.road());
		request.setToDetail(to.detail());
		request.setAiCbm(calc.cbm());
		request.setAiEstAmount(calc.estimate().amount());
		request.setAiEstMin(calc.estimate().min());
		request.setAiEstMax(calc.estimate().max());
		request.setAiAnalysisSec(session.analysisSec());
		request.setAiConfidence(averageConfidence(calc.items()));
		requestMapper.insertRequest(request);

		ReVideo video = new ReVideo();
		video.setRequestId(request.getRequestId());
		video.setFileName(session.videoName());
		video.setFilePath(session.videoPath());
		requestMapper.insertVideo(video);

		for (RequestItem item : calc.items()) {
			item.setRequestId(request.getRequestId());
			requestMapper.insertItem(item);
		}
		createDraftQuote(member, request, calc);
		return request.getRequestId();
	}

	/** 견적서 초안: 요청 내용 + 회원 이름·연락처, 이사비용 = 예상 금액(날짜 미정이면 범위 중간), 계약금 비율 적용 */
	private void createDraftQuote(LoginMember member, MoveRequest request, Calculation calc) {
		long movingCost = priceCalculator.movingCost(calc.estimate(), request.getMoveDate() != null);
		long deposit = priceCalculator.deposit(movingCost);
		Quote quote = new Quote();
		quote.setRequestId(request.getRequestId());
		quote.setMemberId(member.memberId());
		quote.setSourceType("01");
		quote.setStatus("01");
		quote.setContactName(member.name());
		quote.setContactPhone(member.phone());
		quote.setFromAddress(joinAddress(request.getFromAddress(), request.getFromDetail()));
		quote.setToAddress(joinAddress(request.getToAddress(), request.getToDetail()));
		quote.setWorkDate(request.getMoveDate());
		quote.setWorkType(request.getPackType());
		quote.setVolumeTon(priceCalculator.ton(calc.cbm()));
		quote.setMovingCost(movingCost);
		quote.setTotalAmount(movingCost);
		quote.setDepositAmount(deposit);
		quote.setBalanceAmount(movingCost - deposit);
		quoteMapper.insertQuote(quote);

		int order = 1;
		for (RequestItem item : calc.items()) {
			QuoteItem qi = new QuoteItem();
			qi.setQuoteId(quote.getQuoteId());
			qi.setCategory(item.getCategory());
			qi.setItemName(item.getItemName());
			qi.setQuantity(item.getQuantity());
			qi.setSortOrder(order++);
			quoteMapper.insertQuoteItem(qi);
		}
		notificationService.notifyAdmins(member.memberId(), NotificationDetail.QUOTE_RECEIVED.code(), "견적 접수",
				member.name() + " 고객님의 견적 요청이 접수되었습니다.", "/admin/estimates/" + quote.getQuoteId());
	}

	private static QuoteForm.Address checkAddress(QuoteForm.Address a) {
		if (a == null || a.road() == null || a.road().isBlank() || a.road().trim().length() > 200
				|| (a.detail() != null && a.detail().trim().length() > 100)) {
			throw new IllegalArgumentException("주소를 다시 확인해 주세요.");
		}
		String detail = a.detail() == null || a.detail().isBlank() ? null : a.detail().trim();
		return new QuoteForm.Address(a.road().trim(), detail);
	}

	private static LocalDate parseDate(String date) {
		if (date == null || date.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(date);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("날짜를 다시 선택해 주세요.");
		}
	}

	/** AI 품목 신뢰도 평균 (AI 품목이 없으면 null) */
	private static Integer averageConfidence(List<RequestItem> items) {
		List<Integer> values = items.stream().map(RequestItem::getConfidence).filter(Objects::nonNull).toList();
		if (values.isEmpty()) {
			return null;
		}
		return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum())
				.divide(BigDecimal.valueOf(values.size()), 0, RoundingMode.HALF_UP).intValue();
	}

	private static String joinAddress(String road, String detail) {
		return detail == null ? road : road + " " + detail;
	}
}
