package com.codemove.quote;

import java.util.List;

/**
 * 관리자 견적 확인(SCR-A-002) 표시값. 값은 화면 글자 그대로 만들어 둔다.
 * state: 화면 상태 이름 ('' 견적 검토 · no-date 작업일 미정 · approved · confirmed · expired) — 템플릿의 ?state= 미리보기와 같은 이름
 * source: request · photo · pdf
 * SAMPLE: 미리보기(/dev)용 예시 (견적 QT-20260325-0001 홍길동)
 */
public record QuoteDetailView(Long quoteId, String state, String source, String name, String quoteNo,
		String estimateDate, String approvedAt, Long expiresInSeconds, String expiredAt, String houseLabel,
		String packLabel, String preferLabel, String from, String to, String requestedAt, String cbm,
		String estAmount, String confidence, String analysisSec, String estRange, List<ItemRow> items,
		boolean hasVideo, String documentName) {

	/** sub: '전자기기 · 대형', confidence: 0~100 (사용자 추가 품목은 null), user: 고객이 추가한 품목 */
	public record ItemRow(String icon, String name, String sub, int quantity, String volume, Integer confidence,
			boolean review, boolean user) {
	}

	public static final QuoteDetailView SAMPLE = new QuoteDetailView(null, "", "request", "홍길동", "QT-20260325-0001",
			"26.03.25", "26.03.26 14:20", 85268L, "26.03.29 14:20", "가정이사 · 아파트", "반포장", "26.04.12 (일) · 오전",
			"서울 강남구 테헤란로 123, 101동 1203호", "경기 성남시 분당구 판교로 45, 2층", "26.03.25 13:40", "7.7 CBM", "105만원",
			"92%", "18초", "950,000 ~ 1,180,000원",
			List.of(new ItemRow("item-fridge.svg", "냉장고", "전자기기 · 대형", 1, "1.2", 97, false, false),
					new ItemRow("item-washer.svg", "세탁기", "전자기기 · 중형", 1, "0.8", 93, false, false),
					new ItemRow("item-bed.svg", "침대", "가구 · 대형", 1, "1.8", 95, false, false),
					new ItemRow("item-table.svg", "책상", "가구 · 중형", 2, "0.9", 88, false, false),
					new ItemRow("item-table.svg", "의자", "가구 · 소형", 4, "0.4", 61, true, false),
					new ItemRow("item-box.svg", "박스", "생활용품 · 소형", 25, "2.6", null, false, true)),
			true, null);
}
