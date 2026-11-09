package com.codemove.quote;

import java.util.List;

/**
 * 사용자 견적 표시값 (내 이사 견적 정보 탭 · 견적 상세 G-E). 값은 화면 글자 그대로.
 * state: reviewing(01 검토 중 — 금액·견적서는 보여주지 않음) · arrived(02 견적 도착) · confirmed(03) · expired(04)
 * updatedAt: 확정할 때 그대로 돌려보내 그 사이 견적이 바뀌었는지 서버가 확인
 * SAMPLE: 미리보기(/dev)용 예시
 */
public record MyQuoteView(Long id, String state, String badge, String price, String quoteNo, Long expiresInSeconds,
		String updatedAt, String ton, List<ReceiptRow> receipt, String from, String to, String moveDateTime,
		String typeLabel, String cardMeta, String confirmedDate, String cbm, String itemKinds, String analysisSec,
		String confidence, List<AiItem> aiItems) {

	/** 금액 내역 한 줄 (견적서 항목대로: 이사비용 / 사다리 / 옵션) */
	public record ReceiptRow(String label, String sub, String amount) {
	}

	public record AiItem(String icon, String name, int count, String cbm) {
	}

	public static final MyQuoteView SAMPLE = new MyQuoteView(1L, "arrived", "견적 도착", "410,000원", "QT-20260310-0042",
			85268L, null, "1.5톤",
			List.of(new ReceiptRow("이사비용", null, "370,000원"), new ReceiptRow("도착지 사다리 (8층)", null, "20,000원"),
					new ReceiptRow("옵션", "벽걸이TV 탈착·설치", "20,000원")),
			"서울 강남구 테헤란로 123, 301호", "서울 서초구 서초대로 456, 804호", "2026. 03. 18 (수) 오전 10:00",
			"원룸 / 오피스텔 · 일반포장", "원룸 / 오피스텔 · 일반포장 · 3월 18일", "10.27", "7.7 CBM", "6종", "38초", "94%",
			List.of(new AiItem("item-sofa.svg", "소파 3인용", 1, "1.2"), new AiItem("item-bed.svg", "퀸 침대 프레임", 1, "1.8"),
					new AiItem("item-tv.svg", "TV 55인치", 1, "0.4"), new AiItem("item-table.svg", "식탁 + 의자 4개", 1, "0.9"),
					new AiItem("item-wardrobe.svg", "장롱 (3단)", 1, "1.6"), new AiItem("item-box.svg", "기타 박스류", 12, "1.8")));
}
