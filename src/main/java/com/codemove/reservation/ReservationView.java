package com.codemove.reservation;

import java.util.List;

import com.codemove.quote.MyQuoteView;

/**
 * 사용자 예약 표시값 (내 이사 예약 정보 · 완료 탭, 예약 상세 G-F). 값은 화면 글자 그대로.
 * status: RSV_STATUS (01 예약 확정 · 02 이사 완료 · 03 취소)
 * SAMPLE: 미리보기(/dev)용 예시
 */
public record ReservationView(Long id, String status, String statusLabel, String dday, String reservationNo,
		String from, String to, String moveDateTime, String typeLabel, String total, List<MyQuoteView.ReceiptRow> receipt,
		String deposit, String depositStatus, String balance, String memo, String cancelDesc, String cardDate,
		String cardTime, String routeFrom, String routeTo, String completedDate, boolean reviewed) {

	public boolean cancellable() {
		return "01".equals(status);
	}

	/** 홈 예약 카드: 출발지 구 이름 뒤 주소 (서울 강남구 테헤란로 123, 301호 → 테헤란로 123, 301호) */
	public String fromRest() {
		return afterDistrict(from, routeFrom);
	}

	public String toRest() {
		return afterDistrict(to, routeTo);
	}

	/** 쉬운 모드 홈: 3월 18일 오전 10시에 이사해요 (시각이 없으면 3월 18일에 이사해요) */
	public String easyWhen() {
		String date = cardDate.replaceAll("\\s*\\(.\\)$", "");
		if (cardTime == null || cardTime.isBlank()) {
			return date + "에 이사해요";
		}
		return date + " " + cardTime.replace(":00", "시").replaceAll(":(\\d{2})$", "시 $1분") + "에 이사해요";
	}

	private static String afterDistrict(String address, String district) {
		if (address == null || district == null || district.isEmpty()) {
			return address == null ? "" : address;
		}
		int i = address.indexOf(district + " ");
		return i < 0 ? address : address.substring(i + district.length() + 1);
	}

	public static final ReservationView SAMPLE = new ReservationView(1L, "01", "예약 확정", "D-30", "RSV-20260318-1042",
			"서울 강남구 테헤란로 123, 301호", "서울 서초구 서초대로 456, 804호", "2026. 03. 18 (수) 오전 10:00",
			"원룸 / 오피스텔 · 일반포장", "410,000원",
			List.of(new MyQuoteView.ReceiptRow("이사비용", null, "370,000원"),
					new MyQuoteView.ReceiptRow("도착지 사다리 (8층)", null, "20,000원"),
					new MyQuoteView.ReceiptRow("옵션", "벽걸이TV", "20,000원")),
			"41,000원", "26.03.10 결제 완료", "369,000원", "냉장고는 전날 비워둘게요. 도착지 주차는 지하 2층에 가능합니다.",
			"3월 18일 (수) 오전 10:00 일정이 사라져요", "3월 18일 (수)", "오전 10:00", "강남구", "서초구", "25.11.04", false);
}
