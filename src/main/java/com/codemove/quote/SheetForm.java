package com.codemove.quote;

import java.util.List;

/**
 * 견적서 수정 저장값 (estimate.js 가 문서 칸을 그대로 모아 JSON 으로 보냄). 형식 해석·검증·합계 계산은 QuoteService.
 * items: 품목 행마다 8칸 (SheetView.itemRows 와 같은 순서), options: 옵션 8종 순서, transports: 사다리차·승강기·계단작업 순서
 */
public record SheetForm(String companyName, String customer, String phone, String estimateDate, String origin,
		String moveDate, String destination, String transportDate, List<List<String>> items,
		List<OptionForm> options, String workMethod, List<TransportForm> transports, String volume, String note,
		String movingCost, String fromLadderFloor, String departureLadder, String toLadderFloor,
		String destinationLadder) {

	public record OptionForm(boolean on1, boolean on2, String cost) {
	}

	public record TransportForm(boolean from, boolean to) {
	}
}
