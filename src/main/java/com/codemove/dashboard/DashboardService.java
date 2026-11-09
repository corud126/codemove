package com.codemove.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.codemove.common.code.CodeService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 홈(대시보드) SCR-A-016 표시값.
 * 현황 카드: 전체 예약 신청(예약 전체) · 리뷰 등록(전체) · 견적 검토(QUOTE_STATUS 01) · 예약 완료(RSV_STATUS 01 예약 완료 + 02 이사 완료)
 * 주간 예약 현황: 이번 주(월~일) 접수된 예약 수와 요일별 선 그래프 · 최근 예약 내역 3건 (이사 완료는 목록 칩 규칙대로 '예약 완료')
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yy.MM.dd");
	private static final int RECENT = 3;

	/* 그래프 영역 (home.html 의 viewBox 340×130): x 10~330, 점 높이 y 26(가장 많은 날) ~ 91(0건), 바닥 106 */
	private static final double LEFT = 10;
	private static final double RIGHT = 330;
	private static final double TOP = 26;
	private static final double ZERO = 91;
	private static final int FLOOR = 106;

	private final DashboardMapper dashboardMapper;
	private final CodeService codeService;

	/** tone: 조치가 필요한 항목(견적 검토)만 tone-orange, 나머지는 초록 계열 (docs/ui/관리자디자인기준.md) */
	public record Stat(String label, int value, String icon, String tone, String link) {
	}

	public record Recent(String name, String movingDate, String origin, String destination, String status) {
	}

	/** line: 선 경로 · area: 채움 경로 · points: 점 'x,y' (월~일) */
	public record Chart(String line, String area, List<String> points) {
	}

	public record View(String today, List<Stat> stats, int weeklyTotal, Chart chart, List<Recent> recent) {
	}

	public View view(LocalDate today) {
		List<Stat> stats = List.of(
				new Stat("전체 예약 신청", dashboardMapper.countReservations(List.of()), "calendar", "tone-green", "/admin/reservations"),
				new Stat("리뷰 등록", dashboardMapper.countReviews(), "star", "tone-sage", "/admin/reviews"),
				new Stat("견적 검토", dashboardMapper.countQuotes("01"), "document", "tone-orange", "/admin/estimates?status=review"),
				new Stat("예약 완료", dashboardMapper.countReservations(List.of("01", "02")), "calendarCheck", "tone-green",
						"/admin/reservations?status=done"));

		LocalDate monday = today.with(DayOfWeek.MONDAY);
		Map<LocalDate, Integer> byDay = dashboardMapper.countAppliedByDay(monday, monday.plusDays(7)).stream()
				.collect(Collectors.toMap(DailyCount::getAppliedDay, DailyCount::getAppliedCount, Integer::sum));
		int[] week = new int[7];
		for (int i = 0; i < 7; i++) {
			week[i] = byDay.getOrDefault(monday.plusDays(i), 0);
		}
		int weeklyTotal = java.util.Arrays.stream(week).sum();

		List<Recent> recent = dashboardMapper.selectRecentReservations(RECENT).stream()
				.map(r -> new Recent(r.getMemberName(), r.getMoveDate() == null ? "" : r.getMoveDate().format(YMD),
						nv(r.getFromAddress()), nv(r.getToAddress()),
						codeService.name("RSV_STATUS", "03".equals(r.getStatus()) ? "03" : "01")))
				.toList();
		return new View(today.format(TODAY), stats, weeklyTotal, chart(week), recent);
	}

	/** 요일별 건수 → 부드러운 선(점 사이 3차 곡선, 끝점 기울기 0) */
	public static Chart chart(int[] counts) {
		int max = java.util.Arrays.stream(counts).max().orElse(0);
		double step = (RIGHT - LEFT) / (counts.length - 1);
		List<double[]> pts = new ArrayList<>();
		for (int i = 0; i < counts.length; i++) {
			double y = max == 0 ? ZERO : ZERO - (ZERO - TOP) * counts[i] / max;
			pts.add(new double[] { LEFT + step * i, y });
		}
		StringBuilder line = new StringBuilder("M" + n(pts.get(0)[0]) + " " + n(pts.get(0)[1]));
		for (int i = 1; i < pts.size(); i++) {
			double[] a = pts.get(i - 1);
			double[] b = pts.get(i);
			double dx = (b[0] - a[0]) / 2;
			line.append(" C").append(n(a[0] + dx)).append(' ').append(n(a[1])).append(' ')
					.append(n(b[0] - dx)).append(' ').append(n(b[1])).append(' ').append(n(b[0])).append(' ').append(n(b[1]));
		}
		String area = line + " L" + n(RIGHT) + " " + FLOOR + " L" + n(LEFT) + " " + FLOOR + "Z";
		List<String> points = pts.stream().map(p -> n(p[0]) + "," + n(p[1])).toList();
		return new Chart(line.toString(), area, points);
	}

	/** 소수 한 자리 (끝 0 제거) */
	private static String n(double v) {
		double r = Math.round(v * 10) / 10.0;
		return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
	}

	private static String nv(String s) {
		return s == null ? "" : s;
	}
}
