package com.codemove.reservation;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.code.CodeService;
import com.codemove.domain.Quote;
import com.codemove.domain.Reservation;
import com.codemove.quote.QuoteMapper;
import com.codemove.quote.QuoteService;
import com.codemove.schedule.AvailabilityService;

import lombok.RequiredArgsConstructor;

/**
 * 예약. 사용자가 견적을 확정하면 견적서 작업일·작업시각으로 예약(RSV_STATUS 01 · PAY_STATUS 01 결제 대기)을 만든다 (ERD 0·2단계).
 * 이사일 다음 날 0시에 이사 완료(02)로 바꾼다 (ERD Q7). 화면을 열 때도 한 번 더 확인한다.
 * 할 수 없는 동작이면 화면에 보여줄 문구로 IllegalStateException.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

	public static final String BOOKED = "01";
	public static final String COMPLETED = "02";
	public static final String CANCELLED = "03";
	private static final String BY_MEMBER = "01";

	private final QuoteMapper quoteMapper;
	private final QuoteService quoteService;
	private final ReservationMapper reservationMapper;
	private final AvailabilityService availabilityService;
	private final CodeService codeService;

	/**
	 * 견적 확정 → 예약 생성. updatedAt: 고객이 화면에서 본 견적서의 UPDATED_AT (그 사이 관리자가 고쳤으면 막는다).
	 * 만든 예약 ID 를 돌려준다
	 */
	@Transactional
	public Long confirmQuote(Long memberId, Long quoteId, LocalDateTime updatedAt) {
		Quote q = quoteMapper.selectById(quoteId);
		if (q == null || !memberId.equals(q.getMemberId()) || q.getRequestId() == null) {
			throw new IllegalArgumentException("견적을 찾을 수 없어요.");
		}
		if (!QuoteService.APPROVED.equals(q.getStatus())
				|| (q.getExpiresAt() != null && q.getExpiresAt().isBefore(LocalDateTime.now()))) {
			throw new IllegalStateException("확정할 수 없는 견적이에요. 견적 상태를 다시 확인해 주세요.");
		}
		if (updatedAt == null || !updatedAt.equals(q.getUpdatedAt())) {
			throw new IllegalStateException("견적이 변경되었어요. 바뀐 견적을 확인한 뒤 다시 확정해 주세요.");
		}
		// 설정 행을 잠그고 다시 세어 동시에 확정해도 하루 최대 예약을 넘지 않게 (AvailabilityService, ERD Q18)
		if (q.getWorkDate() == null || !availabilityService.isAvailableForConfirm(q.getWorkDate())) {
			throw new IllegalStateException("그날은 예약이 마감되었어요. 채팅 상담으로 날짜를 다시 정해 주세요.");
		}
		if (quoteMapper.confirm(quoteId, updatedAt) == 0) {
			throw new IllegalStateException("견적이 변경되었어요. 바뀐 견적을 확인한 뒤 다시 확정해 주세요.");
		}
		Reservation reservation = new Reservation();
		reservation.setRequestId(q.getRequestId());
		reservation.setMoveDate(q.getWorkDate());
		reservation.setMoveTime(q.getWorkTime());
		reservationMapper.insertReservation(reservation);
		// 알림 단계에서 추가: 관리자에게 '예약 확정' 알림, 고객에게 계약금 안내
		return reservation.getReservationId();
	}

	/** 매일 0시: 이사일이 지난 예약을 이사 완료로 */
	@Scheduled(cron = "0 0 0 * * *")
	@Transactional
	public void completeOverdue() {
		reservationMapper.completeOverdue();
	}

	/** 회원의 예약 (이사일 순, 이사 완료 처리 후) */
	@Transactional
	public List<Reservation> memberReservations(Long memberId) {
		reservationMapper.completeOverdue();
		return reservationMapper.selectByMember(memberId);
	}

	/** 회원 본인 예약 (아니면 null) */
	@Transactional
	public Reservation find(Long memberId, Long reservationId) {
		reservationMapper.completeOverdue();
		return reservationMapper.selectForMember(reservationId, memberId);
	}

	@Transactional
	public void updateMemo(Long memberId, Long reservationId, String memo) {
		Reservation r = reservationMapper.selectForMember(reservationId, memberId);
		if (r == null) {
			throw new IllegalArgumentException("예약을 찾을 수 없어요.");
		}
		String value = memo == null || memo.isBlank() ? null : memo.trim();
		if (value != null && value.length() > 500) {
			throw new IllegalArgumentException("요청사항은 500자까지 입력할 수 있어요.");
		}
		if (reservationMapper.updateMemo(reservationId, value) == 0) {
			throw new IllegalStateException("확정된 예약만 요청사항을 바꿀 수 있어요.");
		}
	}

	/** 회원 예약 취소: 이사일 전까지만. 결제 완료 후 환불은 이번 범위 밖 (ERD 2단계) */
	@Transactional
	public void cancel(Long memberId, Long reservationId) {
		Reservation r = reservationMapper.selectForMember(reservationId, memberId);
		if (r == null) {
			throw new IllegalArgumentException("예약을 찾을 수 없어요.");
		}
		if (!r.getMoveDate().isAfter(LocalDate.now()) || reservationMapper.cancel(reservationId, BY_MEMBER) == 0) {
			throw new IllegalStateException("취소할 수 없는 예약이에요. 업체에 문의해 주세요.");
		}
		// 알림 단계에서 추가: 관리자에게 '예약 취소' 알림
	}

	/** 화면 표시값 */
	public ReservationView view(Reservation r) {
		Quote quote = quoteMapper.selectByRequestId(r.getRequestId());
		QuoteService.Detail d = quoteService.detail(quote.getQuoteId());
		LocalDate date = r.getMoveDate();
		long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
		String weekday = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
		String cardDate = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 (" + weekday + ")";
		String time = r.getMoveTime() == null ? "" : QuoteService.clock(r.getMoveTime());
		long total = nz(quote.getTotalAmount());
		long deposit = nz(quote.getDepositAmount());
		String depositStatus = switch (r.getPayStatus()) {
			case "02" -> r.getDepositPaidAt() == null ? "결제 완료" : r.getDepositPaidAt().format(DateTimeFormatter.ofPattern("yy.MM.dd")) + " 결제 완료";
			default -> codeService.name("PAY_STATUS", r.getPayStatus());
		};
		return new ReservationView(r.getReservationId(), r.getStatus(), codeService.userName("RSV_STATUS", r.getStatus()),
				days == 0 ? "D-DAY" : days > 0 ? "D-" + days : "", r.getReservationNo(), nv(quote.getFromAddress()),
				nv(quote.getToAddress()), QuoteService.dateTime(date, r.getMoveTime()), quoteService.typeLabel(d),
				money(total) + "원", quoteService.receipt(d), money(deposit) + "원", depositStatus,
				money(total - deposit) + "원", nv(r.getMoveMemo()),
				(cardDate + (time.isEmpty() ? "" : " " + time)) + " 일정이 사라져요", cardDate, time,
				district(quote.getFromAddress()), district(quote.getToAddress()),
				date.format(DateTimeFormatter.ofPattern("yy.MM.dd")), reservationMapper.hasReview(r.getReservationId()));
	}

	/** 주소에서 구·군·시 이름 (서울 강남구 테헤란로 123 → 강남구) */
	static String district(String address) {
		if (address == null || address.isBlank()) {
			return "";
		}
		String[] parts = address.trim().split("\\s+");
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].endsWith("구") || parts[i].endsWith("군")) {
				return parts[i];
			}
		}
		return parts.length > 1 ? parts[1] : parts[0];
	}

	private static String money(long n) {
		return NumberFormat.getNumberInstance(Locale.KOREA).format(n);
	}

	private static long nz(Long n) {
		return n == null ? 0 : n;
	}

	private static String nv(String s) {
		return s == null ? "" : s;
	}
}
