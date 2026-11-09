package com.codemove.reservation;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
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
import com.codemove.notification.NotificationDetail;
import com.codemove.notification.NotificationService;
import com.codemove.quote.QuoteMapper;
import com.codemove.quote.QuoteService;
import com.codemove.schedule.AvailabilityService;

import lombok.RequiredArgsConstructor;

/**
 * 예약. 사용자가 견적을 확정하면 견적서 작업일·작업시각으로 예약(RSV_STATUS 01 · PAY_STATUS 01 결제 대기)을 만든다 (ERD 0·2단계).
 * 이사일 다음 날 0시에 이사 완료(02)로 바꾼다 (ERD Q7). 화면을 열 때도 한 번 더 확인한다.
 * 관리자 예약 관리(SCR-A-011): 목록 · [결제 완료](PAY_STATUS 02) · [예약 취소](CANCELLED_BY 02) · [일정 변경](MOVE_DATE·MOVE_TIME).
 * 할 수 없는 동작이면 화면에 보여줄 문구로 IllegalStateException.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

	public static final String BOOKED = "01";
	public static final String COMPLETED = "02";
	public static final String CANCELLED = "03";
	private static final String BY_MEMBER = "01";
	private static final String BY_ADMIN = "02";
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yy.MM.dd");
	private static final DateTimeFormatter YMD_STRICT = DateTimeFormatter.ofPattern("uu.MM.dd").withResolverStyle(ResolverStyle.STRICT);

	private final QuoteMapper quoteMapper;
	private final NotificationService notificationService;
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
		notificationService.notifyAdmins(memberId, NotificationDetail.RESERVATION_CONFIRMED.code(), "예약 확정",
				q.getContactName() + " 고객님이 견적을 확정해 예약이 생성되었습니다.", "/admin/reservations");
		notificationService.notifyMember(memberId, NotificationDetail.RESERVATION_CONFIRMED.code(), "이사 예약이 확정되었어요",
				"예약 내용과 계약금 결제 안내를 확인해 주세요.", "/reservations/" + reservation.getReservationId());
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
		notificationService.notifyAdmins(memberId, NotificationDetail.RESERVATION_CANCELLED.code(), "예약 취소",
				"고객님이 " + r.getMoveDate().getMonthValue() + "월 " + r.getMoveDate().getDayOfMonth() + "일 이사 예약을 취소했습니다.", "/admin/reservations");
	}

	/* ---------- 관리자 예약 관리 (SCR-A-011) ---------- */

	/**
	 * 관리자 목록 행. 값은 화면 글자 그대로(없으면 '-'), total·deposit·balance 는 숫자만.
	 * status·payment·cancelledBy 는 관리자 표시명 (예약 완료·이사 완료·예약 취소 / 결제 대기·결제 완료·결제 취소 / 회원·관리자)
	 */
	public record AdminRow(Long id, String status, String name, String phone, String applied, String service, String pack,
			String moveDate, String moveTime, String origin, String destination, String payment, String total, String memo,
			String paidAt, String cancelledAt, String cancelledBy, String deposit, String balance, String quoteNo,
			String reservationNo) {
	}

	/** 전체 예약 (이사 완료 처리 후, 이사일 최근 순) */
	@Transactional
	public List<AdminRow> adminRows() {
		reservationMapper.completeOverdue();
		return reservationMapper.selectAdminRows().stream()
				.map(r -> new AdminRow(r.getReservationId(), codeService.name("RSV_STATUS", r.getStatus()), dash(r.getMemberName()),
						dash(r.getPhone()), ymd(r.getAppliedAt()), dash(codeService.name("HOUSE_TYPE", r.getHouseType())),
						dash(codeService.name("PACK_TYPE", r.getWorkType() != null ? r.getWorkType() : r.getPackType())),
						r.getMoveDate().format(YMD), dash(r.getMoveTime()), dash(r.getFromAddress()), dash(r.getToAddress()),
						codeService.name("PAY_STATUS", r.getPayStatus()), String.valueOf(nz(r.getTotalAmount())),
						dash(r.getMoveMemo()), ymd(r.getDepositPaidAt()), ymd(r.getCancelledAt()),
						dash(codeService.name("CANCELLED_BY", r.getCancelledBy())), String.valueOf(nz(r.getDepositAmount())),
						String.valueOf(nz(r.getBalanceAmount())), dash(r.getQuoteNo()), r.getReservationNo()))
				.toList();
	}

	/** [결제 완료]: 계약금 결제 대기 → 결제 완료 + 결제일. 결제일(YY.MM.DD)을 돌려준다 */
	@Transactional
	public String completePayment(Long reservationId) {
		if (reservationMapper.selectById(reservationId) == null) {
			throw new IllegalArgumentException("예약을 찾을 수 없어요.");
		}
		if (reservationMapper.completePayment(reservationId) == 0) {
			throw new IllegalStateException("결제 대기 중인 예약만 결제 완료로 바꿀 수 있어요.");
		}
		notifyMemberOf(reservationId, NotificationDetail.DEPOSIT_PAID, "계약금 결제가 완료되었어요", "이사 당일 잔금만 결제하시면 돼요.");
		return ymd(reservationMapper.selectById(reservationId).getDepositPaidAt());
	}

	/** 관리자 예약 취소: 예약 완료(01)만. 취소 주체 관리자(02), 결제 대기였으면 결제 취소(03). 취소일(YY.MM.DD)을 돌려준다 */
	@Transactional
	public String cancelByAdmin(Long reservationId) {
		if (reservationMapper.selectById(reservationId) == null) {
			throw new IllegalArgumentException("예약을 찾을 수 없어요.");
		}
		if (reservationMapper.cancel(reservationId, BY_ADMIN) == 0) {
			throw new IllegalStateException("예약 완료 상태인 예약만 취소할 수 있어요.");
		}
		notifyMemberOf(reservationId, NotificationDetail.RESERVATION_CANCELLED, "이사 예약이 취소되었어요", "자세한 내용은 채팅 상담으로 문의해 주세요.");
		return ymd(reservationMapper.selectById(reservationId).getCancelledAt());
	}

	/**
	 * 관리자 일정 변경: 예약의 MOVE_DATE · MOVE_TIME 만 고친다 (견적서 작업일은 그대로, revision-user.md TBL_RESERVATION).
	 * date: YY.MM.DD, time: 고정 시간대(QuoteService.WORK_TIMES). 날짜가 바뀌면 그날 예약 가능한지(휴무·마감) 확인한다
	 */
	@Transactional
	public void reschedule(Long reservationId, String date, String time) {
		Reservation r = reservationMapper.selectById(reservationId);
		if (r == null) {
			throw new IllegalArgumentException("예약을 찾을 수 없어요.");
		}
		LocalDate moveDate = parseShortDate(date);
		if (time == null || !QuoteService.WORK_TIMES.contains(time)) {
			throw new IllegalArgumentException("예약 시간은 오전 8:00 · 10:00, 오후 1:00 · 3:00 · 5:00 중에서 골라 주세요.");
		}
		if (!BOOKED.equals(r.getStatus())) {
			throw new IllegalStateException("예약 완료 상태인 예약만 일정을 바꿀 수 있어요.");
		}
		if (!moveDate.equals(r.getMoveDate())) {
			if (!moveDate.isAfter(LocalDate.now())) {
				throw new IllegalArgumentException("예약일은 내일 이후 날짜로만 바꿀 수 있어요.");
			}
			// 설정 행을 잠그고 그날 확정 건수를 다시 센다 (동시에 확정·변경해도 하루 최대 예약을 넘지 않게, Q18)
			if (!availabilityService.isAvailableForConfirm(moveDate)) {
				throw new IllegalStateException("그날은 휴무일이거나 예약이 마감되었어요. 다른 날짜를 골라 주세요.");
			}
		}
		if (reservationMapper.updateSchedule(reservationId, moveDate, time) == 0) {
			throw new IllegalStateException("예약 완료 상태인 예약만 일정을 바꿀 수 있어요.");
		}
		notifyMemberOf(reservationId, NotificationDetail.SCHEDULE_CHANGED, "이사 일정이 변경되었어요",
				moveDate.getMonthValue() + "월 " + moveDate.getDayOfMonth() + "일 " + QuoteService.clock(time) + "로 바뀌었어요.");
	}

	/** 예약한 회원에게 관리자 처리 결과 알림 (결제 완료 · 취소 · 일정 변경) */
	private void notifyMemberOf(Long reservationId, NotificationDetail detail, String title, String content) {
		Long memberId = reservationMapper.selectMemberId(reservationId);
		if (memberId != null) {
			notificationService.notifyMember(memberId, detail.code(), title, content, "/reservations/" + reservationId);
		}
	}

	/** 26.10.21 → 2026-10-21 */
	private static LocalDate parseShortDate(String text) {
		try {
			return LocalDate.parse(text == null ? "" : text.trim(), YMD_STRICT);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("예약일은 26.10.21 처럼 입력해 주세요.");
		}
	}

	private static String ymd(LocalDateTime t) {
		return t == null ? "-" : t.format(YMD);
	}

	private static String dash(String s) {
		return s == null || s.isBlank() ? "-" : s;
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
