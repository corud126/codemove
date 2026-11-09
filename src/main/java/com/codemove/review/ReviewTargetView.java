package com.codemove.review;

/**
 * 후기를 쓸 예약 (후기 목록 위 '후기 작성' 카드 G-H09, 후기 작성 화면 G-H10).
 * date: 3월 18일 · sub: 강남구 → 서초구 · 원룸 일반포장 · 3월 18일
 * SAMPLE: 미리보기(/dev)용 예시
 */
public record ReviewTargetView(Long reservationId, String date, String routeFrom, String routeTo, String sub) {

	public static final ReviewTargetView SAMPLE = new ReviewTargetView(1L, "3월 18일", "강남구", "서초구",
			"강남구 → 서초구 · 원룸 일반포장 · 3월 18일");
}
