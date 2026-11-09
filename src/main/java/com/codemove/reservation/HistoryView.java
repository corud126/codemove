package com.codemove.reservation;

import java.util.List;

/**
 * 사용자 더보기 견적내역(G-H05) · 예약내역(G-H07) 표시값. 값은 화면 글자 그대로.
 * SAMPLE_*: 미리보기(/dev)용 예시 (템플릿 더미와 같은 값)
 */
public final class HistoryView {

	private HistoryView() {
	}

	/* ---------- 견적내역 ---------- */

	/** arrived: 가장 최근 견적 도착(02) 카드, 없으면 null. quotes: 전체 견적 (최근 순) */
	public record Estimates(Arrived arrived, List<QuoteRow> quotes) {
	}

	/** 견적 도착 카드: remain 2일 13시간 남음 / 23:41:08 남음, meta 5월 2일 (토) 이사 · 380,000원 */
	public record Arrived(Long id, Long expiresInSeconds, String remain, String route, String meta) {
	}

	/** state: reviewing · arrived · confirmed · expired (필터 키), date: 견적일 2026. 06. 10 */
	public record QuoteRow(Long id, String state, String label, String date, String route, String type) {
	}

	/* ---------- 예약내역 ---------- */

	/** upcoming: 다가오는 예약 확정(01) (이사일 순). past: 이사 완료(02) · 취소(03) 를 연도별로 (최근 순) */
	public record Reservations(List<Upcoming> upcoming, List<Year> past, int pastCount) {
	}

	/** when: 3월 18일 (수) 오전 10:00 · 원룸 / 오피스텔 · 일반포장 */
	public record Upcoming(Long id, String label, String dday, String route, String when) {
	}

	public record Year(String year, List<PastRow> rows) {
	}

	/** state: completed · cancelled (필터 키), date: 이사일 2025. 11. 02 */
	public record PastRow(Long id, String state, String label, String date, String route, String type) {
	}

	public static final Estimates SAMPLE_ESTIMATES = new Estimates(
			new Arrived(1L, null, "2일 13시간 남음", "서초구 → 강동구", "5월 2일 (토) 이사 · 380,000원"),
			List.of(new QuoteRow(1L, "reviewing", "검토 중", "2026. 06. 10", "서초구 → 마포구", "빌라 / 주택 · 반포장"),
					new QuoteRow(1L, "arrived", "견적 도착", "2026. 04. 29", "서초구 → 강동구", "아파트 · 포장이사"),
					new QuoteRow(1L, "confirmed", "견적 확정", "2026. 03. 10", "강남구 → 서초구", "원룸 / 오피스텔 · 일반포장"),
					new QuoteRow(1L, "expired", "만료", "2026. 01. 24", "강남구 → 송파구", "원룸 / 오피스텔 · 일반포장")));

	public static final Reservations SAMPLE_RESERVATIONS = new Reservations(
			List.of(new Upcoming(1L, "예약 확정", "D-30", "강남구 → 서초구", "3월 18일 (수) 오전 10:00 · 원룸 / 오피스텔 · 일반포장")),
			List.of(new Year("2025년", List.of(
					new PastRow(1L, "completed", "이사 완료", "2025. 11. 02", "송파구 → 성동구", "원룸 / 오피스텔 · 반포장"),
					new PastRow(1L, "completed", "이사 완료", "2025. 02. 14", "마포구 → 용산구", "원룸 / 오피스텔 · 일반포장"))),
					new Year("2024년", List.of(
							new PastRow(1L, "cancelled", "취소", "2024. 08. 21", "강동구 → 광진구", "원룸 / 오피스텔 · 일반포장")))),
			3);
}
