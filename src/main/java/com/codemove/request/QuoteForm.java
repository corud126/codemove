package com.codemove.request;

import java.util.List;

/**
 * 이사 견적 6단계 입력값 (quote.js → JSON). 선택지는 화면 표시명으로 받고 서버에서 코드로 바꾼다.
 * date: yyyy-MM-dd, 날짜 미정이면 null (time 도 무시)
 */
public record QuoteForm(List<ItemForm> items, String house, String pack, Address from, Address to, String date,
		String time) {

	/** aiIndex: AI 인식 결과의 순번 (사용자가 추가한 품목은 null). review: '확인 필요' 표시가 남아 있는지 */
	public record ItemForm(String name, String size, int count, Integer aiIndex, boolean review) {
	}

	public record Address(String road, String detail) {
	}
}
