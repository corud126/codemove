package com.codemove.quote;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.codemove.admin.AdminMapper;
import com.codemove.common.code.CodeService;
import com.codemove.domain.Admin;
import com.codemove.domain.MoveRequest;
import com.codemove.domain.Quote;
import com.codemove.domain.QuoteItem;
import com.codemove.domain.QuoteOption;
import com.codemove.domain.ReVideo;
import com.codemove.domain.RequestItem;
import com.codemove.request.ItemCatalog;
import com.codemove.request.MoveRequestMapper;
import com.codemove.request.PriceCalculator;

import lombok.RequiredArgsConstructor;

/**
 * 견적서 (관리자 견적 관리 SCR-A-001~002).
 * 상태 흐름(ERD 0단계): 01 견적 검토 → [승인] 02 견적 승인 → 사용자 [확정] 03 / 만료 시각이 지나면 04 → [다시 승인] 02
 * 저장할 때마다 총 비용·계약금·잔금은 서버에서 다시 계산한다. 견적 확정(03) 후에는 수정·삭제할 수 없다.
 * 입력이 잘못되면 화면에 보여줄 문구로 IllegalArgumentException, 상태 때문에 안 되면 IllegalStateException.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

	public static final String REVIEW = "01";
	public static final String APPROVED = "02";
	public static final String CONFIRMED = "03";
	public static final String EXPIRED = "04";
	public static final String SOURCE_REQUEST = "01";

	/** 작업 시각 고정 시간대 (ERD 4단계) */
	public static final List<String> WORK_TIMES = List.of("08:00", "10:00", "13:00", "15:00", "17:00");
	/** 승인 후 만료까지 (ERD 0단계 제안) */
	private static final Duration EXPIRES_AFTER = Duration.ofHours(72);
	/** 문서 품목 칸 순서 → ITEM_CATEGORY: 전자기기 02 · 가구 01 · 생활용품 03 · 기타 04 */
	static final List<String> SHEET_COLUMNS = List.of("02", "01", "03", "04");
	private static final int MIN_ITEM_ROWS = 8;
	/** 옵션 8종 선택 칸 이름 (QUOTE_OPTION 01~08 순서) */
	static final List<String[]> OPTION_CHOICES = List.of(new String[] { "일반", "그랜드" },
			new String[] { "분해", "조립" }, new String[] { "분해", "조립" }, new String[] { "탈착", "설치" },
			new String[] { "분리", "설치" }, new String[] { "계단", "승강기" }, new String[] { "분리", "설치" },
			new String[] { "탈착", "설치" });

	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yy.MM.dd");
	private static final DateTimeFormatter YMD_HM = DateTimeFormatter.ofPattern("yy.MM.dd HH:mm");
	private static final Pattern DATE = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{2})");
	private static final Pattern WORK_DATE = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{2})(?:\\s+(\\d{2}:\\d{2}))?");
	private static final Pattern QUANTITY = Pattern.compile("\\s*(\\d{1,3})\\s*(\\S{0,5})\\s*");

	private final QuoteMapper quoteMapper;
	private final MoveRequestMapper requestMapper;
	private final AdminMapper adminMapper;
	private final CodeService codeService;
	private final ItemCatalog catalog;
	private final PriceCalculator priceCalculator;
	private final QuoteFileStore fileStore;

	/** 견적 관리 목록 행: 분류 · 이름 · 견적일(YY.MM.DD) */
	public record ListRow(Long id, String status, String name, String date, boolean deletable) {
	}

	/** 만료 시각이 지난 견적 승인을 만료로 바꾼 뒤 전체 목록 */
	@Transactional
	public List<ListRow> list() {
		quoteMapper.expireOverdue();
		return quoteMapper.selectAll().stream()
				.map(q -> new ListRow(q.getQuoteId(), codeService.name("QUOTE_STATUS", q.getStatus()), q.getContactName(),
						q.getEstimateDate().format(YMD), deletable(q)))
				.toList();
	}

	/** 견적서와 연결 데이터. 없으면 null */
	public record Detail(Quote quote, List<QuoteItem> items, List<QuoteOption> options, MoveRequest request,
			List<RequestItem> requestItems, ReVideo video) {
	}

	@Transactional
	public Detail detail(Long quoteId) {
		quoteMapper.expireOverdue();
		Quote quote = quoteMapper.selectById(quoteId);
		if (quote == null) {
			return null;
		}
		MoveRequest request = quote.getRequestId() == null ? null : requestMapper.selectById(quote.getRequestId());
		return new Detail(quote, quoteMapper.selectItems(quoteId), quoteMapper.selectOptions(quoteId), request,
				request == null ? List.of() : requestMapper.selectItems(request.getRequestId()),
				request == null ? null : requestMapper.selectVideo(request.getRequestId()));
	}

	/** 회원의 견적 (최근 순, 만료 처리 후) */
	@Transactional
	public List<Quote> memberQuotes(Long memberId) {
		quoteMapper.expireOverdue();
		return quoteMapper.selectByMember(memberId);
	}

	/* ---------- 화면 표시값 ---------- */

	/** 사용자 견적 표시값. confirmedAt: 견적 확정 시각(예약 신청 시각), 없으면 null */
	public MyQuoteView myQuoteView(Detail d, LocalDateTime confirmedAt) {
		Quote q = d.quote();
		MoveRequest r = d.request();
		String state = switch (q.getStatus()) {
			case APPROVED -> "arrived";
			case CONFIRMED -> "confirmed";
			case EXPIRED -> "expired";
			default -> "reviewing";
		};
		Long expiresIn = q.getExpiresAt() == null ? null
				: Math.max(0, Duration.between(LocalDateTime.now(), q.getExpiresAt()).toSeconds());
		List<MyQuoteView.ReceiptRow> receipt = receipt(d);

		String type = typeLabel(d);
		String when = q.getWorkDate() == null ? "미정" : dateTime(q.getWorkDate(), q.getWorkTime());
		String meta = q.getWorkDate() == null ? type
				: type + " · " + q.getWorkDate().getMonthValue() + "월 " + q.getWorkDate().getDayOfMonth() + "일";

		List<MyQuoteView.AiItem> aiItems = d.requestItems().stream()
				.map(i -> new MyQuoteView.AiItem(catalog.find(i.getItemName()).icon() + ".svg", i.getItemName(), i.getQuantity(),
						i.getVolume().stripTrailingZeros().toPlainString()))
				.toList();
		return new MyQuoteView(q.getQuoteId(), state, codeService.userName("QUOTE_STATUS", q.getStatus()),
				money(nz(q.getTotalAmount())) + "원", q.getQuoteNo(), expiresIn,
				q.getUpdatedAt() == null ? null : q.getUpdatedAt().toString(),
				q.getVolumeTon() == null ? "" : q.getVolumeTon().stripTrailingZeros().toPlainString() + "톤", receipt,
				nv(q.getFromAddress()), nv(q.getToAddress()), when, type, meta,
				confirmedAt == null ? null : confirmedAt.format(DateTimeFormatter.ofPattern("MM.dd")),
				r == null || r.getAiCbm() == null ? "" : r.getAiCbm().stripTrailingZeros().toPlainString() + " CBM",
				aiItems.size() + "종", r == null || r.getAiAnalysisSec() == null ? null : r.getAiAnalysisSec() + "초",
				r == null || r.getAiConfidence() == null ? null : r.getAiConfidence() + "%", aiItems);
	}

	/** 금액 내역 (견적서 항목대로: 이사비용 / 출발지·도착지 사다리 / 옵션) — 비용이 있는 줄만 */
	public List<MyQuoteView.ReceiptRow> receipt(Detail d) {
		Quote q = d.quote();
		List<MyQuoteView.ReceiptRow> receipt = new ArrayList<>();
		receipt.add(new MyQuoteView.ReceiptRow("이사비용", null, money(nz(q.getMovingCost())) + "원"));
		if (nz(q.getFromLadderCost()) > 0) {
			receipt.add(new MyQuoteView.ReceiptRow(ladderLabel("출발지", q.getFromLadderFloor()), null, money(q.getFromLadderCost()) + "원"));
		}
		if (nz(q.getToLadderCost()) > 0) {
			receipt.add(new MyQuoteView.ReceiptRow(ladderLabel("도착지", q.getToLadderFloor()), null, money(q.getToLadderCost()) + "원"));
		}
		long optionCost = d.options().stream().mapToLong(QuoteOption::getOptionCost).sum();
		if (optionCost > 0) {
			List<String> picked = new ArrayList<>();
			for (QuoteOption o : d.options()) {
				int i = Integer.parseInt(o.getOptionCode()) - 1;
				List<String> choices = new ArrayList<>();
				if ("Y".equals(o.getChoice1Yn())) {
					choices.add(OPTION_CHOICES.get(i)[0]);
				}
				if ("Y".equals(o.getChoice2Yn())) {
					choices.add(OPTION_CHOICES.get(i)[1]);
				}
				picked.add(codeService.name("QUOTE_OPTION", o.getOptionCode()) + (choices.isEmpty() ? "" : " " + String.join("·", choices)));
			}
			receipt.add(new MyQuoteView.ReceiptRow("옵션", String.join(", ", picked), money(optionCost) + "원"));
		}
		return receipt;
	}

	/** 원룸 / 오피스텔 · 일반포장 (견적서 작업내용이 있으면 그것, 없으면 요청 포장 방식) */
	public String typeLabel(Detail d) {
		MoveRequest r = d.request();
		if (r == null) {
			return "";
		}
		String pack = d.quote().getWorkType() != null ? d.quote().getWorkType() : r.getPackType();
		return codeService.userName("HOUSE_TYPE", r.getHouseType()) + " · " + codeService.userName("PACK_TYPE", pack);
	}

	/** 2026. 03. 18 (수) 오전 10:00 */
	public static String dateTime(LocalDate date, String time) {
		return date.format(DateTimeFormatter.ofPattern("yyyy. MM. dd")) + " ("
				+ date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")"
				+ (time == null ? "" : " " + clock(time));
	}

	/** 08:00 → 오전 8:00, 13:00 → 오후 1:00 */
	public static String clock(String hhmm) {
		int h = Integer.parseInt(hhmm.substring(0, 2));
		String m = hhmm.substring(3);
		return (h < 12 ? "오전 " : "오후 ") + (h % 12 == 0 ? 12 : h % 12) + ":" + m;
	}

	private static String ladderLabel(String where, Integer floor) {
		return where + " 사다리" + (floor == null ? "" : " (" + floor + "층)");
	}

	public SheetView sheetView(Detail d) {
		Quote q = d.quote();
		List<List<String>> columns = new ArrayList<>();
		SHEET_COLUMNS.forEach(c -> columns.add(new ArrayList<>()));
		for (QuoteItem item : d.items()) {
			int col = Math.max(0, SHEET_COLUMNS.indexOf(item.getCategory()));
			columns.get(col).add(item.getItemName() + "\u0000" + item.getQuantity() + item.getUnit());
		}
		int rows = Math.max(MIN_ITEM_ROWS, columns.stream().mapToInt(List::size).max().orElse(0));
		List<List<String>> itemRows = new ArrayList<>();
		for (int r = 0; r < rows; r++) {
			List<String> row = new ArrayList<>();
			for (List<String> col : columns) {
				String[] cell = r < col.size() ? col.get(r).split("\u0000") : new String[] { "", "" };
				row.add(cell[0]);
				row.add(cell[1]);
			}
			itemRows.add(row);
		}

		Map<String, QuoteOption> byCode = new java.util.HashMap<>();
		d.options().forEach(o -> byCode.put(o.getOptionCode(), o));
		List<SheetView.OptionRow> options = new ArrayList<>();
		long optionCost = 0;
		for (int i = 0; i < OPTION_CHOICES.size(); i++) {
			String code = String.format("%02d", i + 1);
			QuoteOption o = byCode.get(code);
			long cost = o == null ? 0 : o.getOptionCost();
			optionCost += cost;
			options.add(new SheetView.OptionRow(codeService.name("QUOTE_OPTION", code), OPTION_CHOICES.get(i)[0],
					OPTION_CHOICES.get(i)[1], o != null && "Y".equals(o.getChoice1Yn()),
					o != null && "Y".equals(o.getChoice2Yn()), money(cost)));
		}

		long fromLadder = nz(q.getFromLadderCost());
		long toLadder = nz(q.getToLadderCost());
		Admin signer = q.getAdminId() == null ? null : adminMapper.selectById(q.getAdminId());
		String moveDate = q.getWorkDate() == null ? ""
				: q.getWorkDate().format(YMD) + (q.getWorkTime() == null ? "" : " " + q.getWorkTime());
		return new SheetView(q.getCompanyName(), q.getContactName(), nv(q.getContactPhone()), q.getEstimateDate().format(YMD),
				nv(q.getFromAddress()), moveDate, nv(q.getToAddress()),
				q.getTransportDate() == null ? "" : q.getTransportDate().format(YMD), itemRows, options,
				q.getWorkType() == null ? "" : codeService.name("PACK_TYPE", q.getWorkType()),
				List.of(new SheetView.TransportRow("사다리차", yes(q.getFromLadderUse()), yes(q.getToLadderUse())),
						new SheetView.TransportRow("승강기", yes(q.getFromElevatorUse()), yes(q.getToElevatorUse())),
						new SheetView.TransportRow("계단작업", yes(q.getFromStairsUse()), yes(q.getToStairsUse()))),
				q.getVolumeTon() == null ? "" : q.getVolumeTon().stripTrailingZeros().toPlainString() + "톤", nv(q.getNote()),
				money(nz(q.getMovingCost())), money(fromLadder + toLadder), money(optionCost), money(nz(q.getTotalAmount())),
				q.getFromLadderFloor() == null ? "" : String.valueOf(q.getFromLadderFloor()), money(fromLadder),
				q.getToLadderFloor() == null ? "" : String.valueOf(q.getToLadderFloor()), money(toLadder),
				money(fromLadder + toLadder), money(nz(q.getDepositAmount())), money(nz(q.getBalanceAmount())),
				signer == null ? "　(서명) / H.P　" : signer.getAdminName() + "　(서명) / H.P　" + signer.getPhone());
	}

	public QuoteDetailView detailView(Detail d) {
		Quote q = d.quote();
		MoveRequest r = d.request();
		String state = switch (q.getStatus()) {
			case APPROVED -> "approved";
			case CONFIRMED -> "confirmed";
			case EXPIRED -> "expired";
			default -> q.getWorkDate() == null ? "no-date" : "";
		};
		String source = switch (q.getSourceType()) {
			case "02" -> "photo";
			case "03" -> "pdf";
			default -> "request";
		};
		Long expiresIn = q.getExpiresAt() == null ? null
				: Math.max(0, Duration.between(LocalDateTime.now(), q.getExpiresAt()).toSeconds());

		List<QuoteDetailView.ItemRow> items = d.requestItems().stream()
				.map(i -> new QuoteDetailView.ItemRow(catalog.find(i.getItemName()).icon() + ".svg", i.getItemName(),
						codeService.name("ITEM_CATEGORY", i.getCategory()) + " · " + codeService.name("ITEM_SIZE", i.getItemSize()),
						i.getQuantity(), i.getVolume().stripTrailingZeros().toPlainString(), i.getConfidence(),
						"Y".equals(i.getNeedsReview()), "02".equals(i.getSource())))
				.toList();

		String prefer = r == null || r.getMoveDate() == null ? "미정"
				: dateWithDay(r.getMoveDate()) + " · " + codeService.name("PREFER_TIME", r.getPreferTime());
		return new QuoteDetailView(q.getQuoteId(), state, source, q.getContactName(), q.getQuoteNo(),
				q.getEstimateDate().format(YMD), q.getApprovedAt() == null ? null : q.getApprovedAt().format(YMD_HM),
				expiresIn, q.getExpiresAt() == null ? null : q.getExpiresAt().format(YMD_HM),
				r == null ? "" : codeService.name("HOUSE_TYPE", r.getHouseType()) + " · " + codeService.userName("HOUSE_TYPE", r.getHouseType()),
				r == null ? "" : codeService.name("PACK_TYPE", r.getPackType()), prefer,
				r == null ? "" : joinAddress(r.getFromAddress(), r.getFromDetail()),
				r == null ? "" : joinAddress(r.getToAddress(), r.getToDetail()),
				r == null ? "" : r.getRequestedAt().format(YMD_HM),
				r == null || r.getAiCbm() == null ? "" : r.getAiCbm().stripTrailingZeros().toPlainString() + " CBM",
				r == null || r.getAiEstAmount() == null ? "" : manwon(r.getAiEstAmount()),
				r == null || r.getAiConfidence() == null ? null : r.getAiConfidence() + "%",
				r == null || r.getAiAnalysisSec() == null ? null : r.getAiAnalysisSec() + "초",
				r == null || r.getAiEstMin() == null ? "" : money(r.getAiEstMin()) + " ~ " + money(r.getAiEstMax()) + "원",
				items, d.video() != null, q.getDocumentPath() == null ? null : java.nio.file.Path.of(q.getDocumentPath()).getFileName().toString());
	}

	/* ---------- 저장 · 승인 · 삭제 ---------- */

	/** 견적서 저장. 견적 승인(02) 상태에서 고치면 만료 시각을 다시 시작한다 (ERD 0단계 승인 후 수정 규칙) */
	@Transactional
	public void save(Long quoteId, SheetForm f) {
		Quote q = quoteMapper.selectById(quoteId);
		if (q == null) {
			throw new IllegalArgumentException("견적서를 찾을 수 없어요.");
		}
		if (CONFIRMED.equals(q.getStatus())) {
			throw new IllegalStateException("확정된 견적은 수정할 수 없어요. 변경은 채팅 상담으로 처리해 주세요.");
		}
		Parsed parsed = applySheet(q, f);
		if (APPROVED.equals(q.getStatus())) {
			q.setExpiresAt(expiresAtFor(q));
		}
		if (quoteMapper.updateSheet(q) == 0) {
			throw new IllegalStateException("확정된 견적은 수정할 수 없어요. 변경은 채팅 상담으로 처리해 주세요.");
		}

		quoteMapper.deleteItems(quoteId);
		insertLines(quoteId, parsed);
		// 알림 단계에서 추가: 견적 승인 상태에서 고치면 고객에게 "견적이 변경되었어요"
	}

	/** 문서 칸에서 읽은 품목 · 옵션 (견적서에 저장하기 전) */
	record Parsed(List<QuoteItem> items, List<QuoteOption> options) {
	}

	/**
	 * 문서 칸 값을 해석해 견적서 본문에 옮기고 총 비용·계약금·잔금을 다시 계산한다 (견적서 저장 · 사진 등록 공용).
	 * 품목·옵션은 저장하지 않고 돌려준다. 잘못된 값이면 IllegalArgumentException
	 */
	Parsed applySheet(Quote q, SheetForm f) {
		q.setCompanyName(required(f.companyName(), 50, "업체명"));
		q.setContactName(required(f.customer(), 50, "담당자"));
		q.setContactPhone(optional(f.phone(), 20, "연락처"));
		q.setEstimateDate(requiredDate(f.estimateDate(), "견적일"));
		q.setFromAddress(optional(f.origin(), 300, "출발지"));
		q.setToAddress(optional(f.destination(), 300, "도착지"));
		parseWorkDate(q, f.moveDate());
		q.setTransportDate(optionalDate(f.transportDate(), "운송일"));
		q.setWorkType(blank(f.workMethod()) ? null : codeService.codeOf("PACK_TYPE", f.workMethod()));
		List<SheetForm.TransportForm> t = f.transports() == null ? List.of() : f.transports();
		q.setFromLadderUse(yn(t, 0, true));
		q.setToLadderUse(yn(t, 0, false));
		q.setFromElevatorUse(yn(t, 1, true));
		q.setToElevatorUse(yn(t, 1, false));
		q.setFromStairsUse(yn(t, 2, true));
		q.setToStairsUse(yn(t, 2, false));
		q.setVolumeTon(parseTon(f.volume()));
		q.setNote(optional(f.note(), 500, "비고"));
		q.setMovingCost(parseMoney(f.movingCost()));
		q.setFromLadderFloor(parseFloor(f.fromLadderFloor()));
		q.setFromLadderCost(parseMoney(f.departureLadder()));
		q.setToLadderFloor(parseFloor(f.toLadderFloor()));
		q.setToLadderCost(parseMoney(f.destinationLadder()));

		List<QuoteOption> options = parseOptions(q.getQuoteId(), f.options());
		long optionCost = options.stream().mapToLong(QuoteOption::getOptionCost).sum();
		long total = q.getMovingCost() + q.getFromLadderCost() + q.getToLadderCost() + optionCost;
		setAmounts(q, total);
		return new Parsed(parseItems(q.getQuoteId(), f.items()), options);
	}

	/** 총 비용과 계약금(설정 비율)·잔금 */
	void setAmounts(Quote q, long total) {
		long deposit = priceCalculator.deposit(total);
		q.setTotalAmount(total);
		q.setDepositAmount(deposit);
		q.setBalanceAmount(total - deposit);
	}

	/** 품목은 문서 순서대로, 옵션은 기존 것을 지우고 다시 넣는다 (품목은 호출 쪽에서 지움) */
	void insertLines(Long quoteId, Parsed parsed) {
		int order = 1;
		for (QuoteItem item : parsed.items()) {
			item.setQuoteId(quoteId);
			item.setSortOrder(order++);
			quoteMapper.insertQuoteItem(item);
		}
		quoteMapper.deleteOptions(quoteId);
		for (QuoteOption option : parsed.options()) {
			option.setQuoteId(quoteId);
			quoteMapper.insertOption(option);
		}
	}

	/** 견적 승인 · 다시 승인. 작업일이 없으면 승인할 수 없다 (ERD 2단계 Q6) */
	@Transactional
	public void approve(Long quoteId, Long adminId) {
		Quote q = quoteMapper.selectById(quoteId);
		if (q == null) {
			throw new IllegalArgumentException("견적서를 찾을 수 없어요.");
		}
		if (q.getWorkDate() == null) {
			throw new IllegalStateException("작업일을 입력해야 승인할 수 있어요.");
		}
		q.setAdminId(adminId);
		q.setExpiresAt(expiresAtFor(q));
		if (quoteMapper.approve(q) == 0) {
			throw new IllegalStateException("이미 승인했거나 확정된 견적이에요.");
		}
		// 알림 단계에서 추가: 고객에게 '견적 도착' 알림
	}

	/**
	 * 견적 삭제 (11/09 결정): 관리자가 직접 등록한 사진·PDF 견적(SOURCE_TYPE 02·03)만 지울 수 있다.
	 * 고객이 보낸 견적 요청(01)으로 만든 견적은 고객 화면에서 사라지면 안 되므로 지우지 않는다 (검토 상태로 두거나 만료).
	 */
	@Transactional
	public String delete(Long quoteId) {
		Quote q = quoteMapper.selectById(quoteId);
		if (q == null) {
			throw new IllegalArgumentException("견적서를 찾을 수 없어요.");
		}
		if (SOURCE_REQUEST.equals(q.getSourceType())) {
			throw new IllegalStateException("고객이 요청한 견적은 삭제할 수 없어요.");
		}
		quoteMapper.deleteItems(quoteId);
		quoteMapper.deleteOptions(quoteId);
		quoteMapper.deleteQuote(quoteId);
		// 원본 파일은 삭제가 확정된 뒤 지운다 (롤백되면 그대로 둠)
		String document = q.getDocumentPath();
		if (document != null) {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						fileStore.delete(document);
					}
				});
			} else {
				fileStore.delete(document);
			}
		}
		return q.getContactName();
	}

	/** 원본 파일 (사진·PDF 등록). 없거나 파일이 사라졌으면 null */
	public java.nio.file.Path documentFile(Long quoteId) {
		Quote q = quoteMapper.selectById(quoteId);
		if (q == null || q.getDocumentPath() == null) {
			return null;
		}
		java.nio.file.Path p = java.nio.file.Path.of(q.getDocumentPath());
		return java.nio.file.Files.isRegularFile(p) ? p : null;
	}

	/** 만료 규칙은 견적 요청(01)에만 (ERD Q3). 사진·PDF 등록 견적은 만료 시각 없음 */
	static LocalDateTime expiresAtFor(Quote q) {
		return SOURCE_REQUEST.equals(q.getSourceType()) ? expiresAt(q.getWorkDate()) : null;
	}

	/** 목록에서 삭제 버튼을 보여줄지 (사진·PDF 등록만) */
	public static boolean deletable(Quote q) {
		return !SOURCE_REQUEST.equals(q.getSourceType());
	}

	/** 만료 시각 = 지금 + 72시간. 단 작업일이 그보다 빠르면 작업일 전날 23:59 */
	static LocalDateTime expiresAt(LocalDate workDate) {
		LocalDateTime limit = LocalDateTime.now().plus(EXPIRES_AFTER).withNano(0);
		if (workDate != null) {
			LocalDateTime dayBefore = workDate.minusDays(1).atTime(23, 59);
			if (dayBefore.isBefore(limit)) {
				return dayBefore;
			}
		}
		return limit;
	}

	/* ---------- 문서 칸 해석 ---------- */

	private void parseWorkDate(Quote q, String text) {
		if (blank(text)) {
			q.setWorkDate(null);
			q.setWorkTime(null);
			return;
		}
		Matcher m = WORK_DATE.matcher(text.trim());
		if (!m.matches()) {
			throw new IllegalArgumentException("작업일은 26.04.12 08:00 처럼 입력해 주세요.");
		}
		q.setWorkDate(requiredDate(m.group(1), "작업일"));
		String time = m.group(2);
		if (time != null && !WORK_TIMES.contains(time)) {
			throw new IllegalArgumentException("작업 시각은 08:00 · 10:00 · 13:00 · 15:00 · 17:00 중에서 입력해 주세요.");
		}
		q.setWorkTime(time);
	}

	private List<QuoteItem> parseItems(Long quoteId, List<List<String>> rows) {
		List<QuoteItem> items = new ArrayList<>();
		if (rows == null) {
			return items;
		}
		// 칸 순서대로(전자기기 → 가구 → 생활용품 → 기타), 칸 안에서는 위에서 아래로
		for (int col = 0; col < SHEET_COLUMNS.size(); col++) {
			for (List<String> row : rows) {
				String name = row.size() > col * 2 ? row.get(col * 2) : null;
				if (blank(name)) {
					continue;
				}
				String qty = row.size() > col * 2 + 1 ? row.get(col * 2 + 1) : "";
				QuoteItem item = new QuoteItem();
				item.setQuoteId(quoteId);
				item.setCategory(SHEET_COLUMNS.get(col));
				item.setItemName(required(name, 50, "품목"));
				if (blank(qty)) {
					item.setQuantity(1);
					item.setUnit("개");
				} else {
					Matcher m = QUANTITY.matcher(qty);
					if (!m.matches() || Integer.parseInt(m.group(1)) < 1) {
						throw new IllegalArgumentException("'" + name.trim() + "' 수량은 숫자와 단위로 입력해 주세요. (예: 2개)");
					}
					item.setQuantity(Integer.parseInt(m.group(1)));
					item.setUnit(m.group(2).isEmpty() ? "개" : m.group(2));
				}
				items.add(item);
			}
		}
		return items;
	}

	/** 선택하거나 비용이 있는 옵션만 저장 */
	private List<QuoteOption> parseOptions(Long quoteId, List<SheetForm.OptionForm> forms) {
		List<QuoteOption> options = new ArrayList<>();
		if (forms == null) {
			return options;
		}
		for (int i = 0; i < Math.min(forms.size(), OPTION_CHOICES.size()); i++) {
			SheetForm.OptionForm f = forms.get(i);
			long cost = parseMoney(f.cost());
			if (!f.on1() && !f.on2() && cost == 0) {
				continue;
			}
			QuoteOption o = new QuoteOption();
			o.setQuoteId(quoteId);
			o.setOptionCode(String.format("%02d", i + 1));
			o.setChoice1Yn(f.on1() ? "Y" : "N");
			o.setChoice2Yn(f.on2() ? "Y" : "N");
			o.setOptionCost(cost);
			options.add(o);
		}
		return options;
	}

	static long parseMoney(String text) {
		String digits = text == null ? "" : text.replaceAll("\\D", "");
		if (digits.length() > 10) {
			throw new IllegalArgumentException("금액이 너무 커요.");
		}
		return digits.isEmpty() ? 0 : Long.parseLong(digits);
	}

	private static Integer parseFloor(String text) {
		String digits = text == null ? "" : text.replaceAll("\\D", "");
		if (digits.length() > 3) {
			throw new IllegalArgumentException("사다리 층수를 다시 확인해 주세요.");
		}
		return digits.isEmpty() ? null : Integer.valueOf(digits);
	}

	private static BigDecimal parseTon(String text) {
		String num = text == null ? "" : text.replaceAll("[^0-9.]", "");
		if (num.isEmpty()) {
			return null;
		}
		try {
			BigDecimal ton = new BigDecimal(num).setScale(1, java.math.RoundingMode.HALF_UP);
			if (ton.compareTo(new BigDecimal("999.9")) > 0) {
				throw new NumberFormatException();
			}
			return ton;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("이사물량은 3.5톤 처럼 입력해 주세요.");
		}
	}

	static LocalDate requiredDate(String text, String label) {
		LocalDate date = optionalDate(text, label);
		if (date == null) {
			throw new IllegalArgumentException(label + josa(label, "을", "를") + " 입력해 주세요.");
		}
		return date;
	}

	private static LocalDate optionalDate(String text, String label) {
		if (blank(text)) {
			return null;
		}
		Matcher m = DATE.matcher(text.trim());
		try {
			if (!m.matches()) {
				throw new IllegalArgumentException();
			}
			return LocalDate.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(label + josa(label, "은", "는") + " 26.04.12 처럼 입력해 주세요.");
		}
	}

	static String required(String text, int max, String label) {
		String v = optional(text, max, label);
		if (v == null) {
			throw new IllegalArgumentException(label + josa(label, "을", "를") + " 입력해 주세요.");
		}
		return v;
	}

	static String optional(String text, int max, String label) {
		if (blank(text)) {
			return null;
		}
		String v = text.trim();
		if (v.length() > max) {
			throw new IllegalArgumentException(label + josa(label, "은", "는") + " " + max + "자까지 입력할 수 있어요.");
		}
		return v;
	}

	private static String yn(List<SheetForm.TransportForm> t, int index, boolean from) {
		if (index >= t.size()) {
			return "N";
		}
		return (from ? t.get(index).from() : t.get(index).to()) ? "Y" : "N";
	}

	/* ---------- 표시 형식 ---------- */

	static String money(long n) {
		return NumberFormat.getNumberInstance(Locale.KOREA).format(n);
	}

	/** 105만원 (만 단위로 떨어지지 않으면 1,050,500원) */
	private static String manwon(long n) {
		return n % 10000 == 0 ? money(n / 10000) + "만원" : money(n) + "원";
	}

	private static String dateWithDay(LocalDate d) {
		return d.format(YMD) + " (" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	private static String joinAddress(String road, String detail) {
		return detail == null ? road : road + ", " + detail;
	}

	private static long nz(Long n) {
		return n == null ? 0 : n;
	}

	private static String nv(String s) {
		return s == null ? "" : s;
	}

	private static boolean yes(String yn) {
		return "Y".equals(yn);
	}

	static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	/** 마지막 글자 받침에 맞는 조사 (을/를, 은/는) */
	private static String josa(String word, String withFinal, String withoutFinal) {
		char last = word.charAt(word.length() - 1);
		boolean hasFinal = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
		return hasFinal ? withFinal : withoutFinal;
	}
}
