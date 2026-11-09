package com.codemove.reservation;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.code.CodeService;
import com.codemove.domain.Reservation;
import com.codemove.quote.QuoteMapper;
import com.codemove.quote.QuoteService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 견적내역 · 예약내역 (더보기 G-H05 · G-H07) 과 홈 상태 카드 (SCR-U-001~002).
 * 화면을 열 때 만료 시각이 지난 견적은 만료(04), 이사일이 지난 예약은 이사 완료(02)로 먼저 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

	private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy. MM. dd");

	private final QuoteMapper quoteMapper;
	private final ReservationMapper reservationMapper;
	private final ReservationService reservationService;
	private final CodeService codeService;

	/* ---------- 견적내역 ---------- */

	@Transactional
	public HistoryView.Estimates estimates(Long memberId) {
		quoteMapper.expireOverdue();
		List<QuoteHistoryRow> rows = reservationMapper.selectQuoteHistory(memberId);
		HistoryView.Arrived arrived = rows.stream().filter(q -> QuoteService.APPROVED.equals(q.getStatus())).findFirst()
				.map(this::arrived).orElse(null);
		List<HistoryView.QuoteRow> quotes = rows.stream()
				.map(q -> new HistoryView.QuoteRow(q.getQuoteId(), quoteState(q.getStatus()),
						codeService.userName("QUOTE_STATUS", q.getStatus()),
						q.getEstimateDate() == null ? "" : q.getEstimateDate().format(DOT_DATE),
						route(q.getFromAddress(), q.getToAddress()), typeLabel(q.getHouseType(), q.getWorkType(), q.getPackType())))
				.toList();
		return new HistoryView.Estimates(arrived, quotes);
	}

	private HistoryView.Arrived arrived(QuoteHistoryRow q) {
		Long left = q.getExpiresAt() == null ? null
				: Math.max(0, Duration.between(LocalDateTime.now(), q.getExpiresAt()).toSeconds());
		String price = money(q.getTotalAmount() == null ? 0 : q.getTotalAmount()) + "원";
		String meta = q.getWorkDate() == null ? price : monthDay(q.getWorkDate()) + " 이사 · " + price;
		return new HistoryView.Arrived(q.getQuoteId(), left, left == null ? "" : remain(left), route(q.getFromAddress(), q.getToAddress()), meta);
	}

	/** 남은 시간: 24시간 이상 N일 N시간, 미만 HH:MM:SS (revision-user.md 견적 만료 표시 규칙) */
	static String remain(long seconds) {
		if (seconds >= 86400) {
			return seconds / 86400 + "일 " + (seconds % 86400) / 3600 + "시간 남음";
		}
		return String.format("%02d:%02d:%02d 남음", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
	}

	private static String quoteState(String status) {
		return switch (status) {
			case QuoteService.APPROVED -> "arrived";
			case QuoteService.CONFIRMED -> "confirmed";
			case QuoteService.EXPIRED -> "expired";
			default -> "reviewing";
		};
	}

	/* ---------- 예약내역 ---------- */

	@Transactional
	public HistoryView.Reservations reservations(Long memberId) {
		reservationMapper.completeOverdue();
		List<ReservationRow> rows = reservationMapper.selectHistoryByMember(memberId); // 이사일 최근 순

		List<HistoryView.Upcoming> upcoming = new ArrayList<>();
		Map<String, List<HistoryView.PastRow>> years = new LinkedHashMap<>();
		int pastCount = 0;
		for (ReservationRow r : rows) {
			String type = typeLabel(r.getHouseType(), r.getWorkType(), r.getPackType());
			String route = route(r.getFromAddress(), r.getToAddress());
			if (ReservationService.BOOKED.equals(r.getStatus())) {
				String time = r.getMoveTime() == null ? "" : " " + QuoteService.clock(r.getMoveTime());
				upcoming.add(0, new HistoryView.Upcoming(r.getReservationId(), codeService.userName("RSV_STATUS", r.getStatus()),
						dday(r.getMoveDate()), route, monthDay(r.getMoveDate()) + time + (type.isEmpty() ? "" : " · " + type)));
			} else {
				pastCount++;
				years.computeIfAbsent(r.getMoveDate().getYear() + "년", y -> new ArrayList<>())
						.add(new HistoryView.PastRow(r.getReservationId(),
								ReservationService.CANCELLED.equals(r.getStatus()) ? "cancelled" : "completed",
								codeService.userName("RSV_STATUS", r.getStatus()), r.getMoveDate().format(DOT_DATE), route, type));
			}
		}
		List<HistoryView.Year> past = years.entrySet().stream().map(e -> new HistoryView.Year(e.getKey(), e.getValue())).toList();
		return new HistoryView.Reservations(upcoming, past, pastCount);
	}

	/* ---------- 홈 상태 카드 ---------- */

	/**
	 * 홈 상태: state = arrived(견적 도착 — 확인·확정할 일이 있음) > booked(다가오는 예약 확정) > reviewing(검토 중) > none.
	 * booking: booked 일 때 가장 빠른 다가오는 예약
	 */
	public record Home(String state, ReservationView booking) {
	}

	@Transactional
	public Home home(Long memberId) {
		quoteMapper.expireOverdue();
		List<String> quoteStatuses = reservationMapper.selectQuoteHistory(memberId).stream().map(QuoteHistoryRow::getStatus).toList();
		if (quoteStatuses.contains(QuoteService.APPROVED)) {
			return new Home("arrived", null);
		}
		Reservation upcoming = reservationService.memberReservations(memberId).stream()
				.filter(r -> ReservationService.BOOKED.equals(r.getStatus())).findFirst().orElse(null);
		if (upcoming != null) {
			return new Home("booked", reservationService.view(upcoming));
		}
		if (quoteStatuses.contains(QuoteService.REVIEW)) {
			return new Home("reviewing", null);
		}
		return new Home("none", null);
	}

	/* ---------- 표시 형식 ---------- */

	/** 원룸 / 오피스텔 · 일반포장 (견적서 작업내용이 있으면 그것, 없으면 요청 포장 방식). 견적 요청이 없으면 빈 값 */
	private String typeLabel(String houseType, String workType, String packType) {
		if (houseType == null) {
			return "";
		}
		String pack = codeService.userName("PACK_TYPE", workType != null ? workType : packType);
		return codeService.userName("HOUSE_TYPE", houseType) + (pack == null ? "" : " · " + pack);
	}

	/** 강남구 → 서초구 */
	private static String route(String from, String to) {
		return ReservationService.district(from) + " → " + ReservationService.district(to);
	}

	/** 3월 18일 (수) */
	private static String monthDay(LocalDate d) {
		return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	private static String dday(LocalDate d) {
		long days = ChronoUnit.DAYS.between(LocalDate.now(), d);
		return days == 0 ? "D-DAY" : days > 0 ? "D-" + days : "";
	}

	private static String money(long n) {
		return NumberFormat.getNumberInstance(Locale.KOREA).format(n);
	}
}
