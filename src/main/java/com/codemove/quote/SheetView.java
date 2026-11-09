package com.codemove.quote;

import java.util.List;

/**
 * 견적서/계약서 문서 표시값 (templates/admin/estimate/sheet.html). 날짜·금액은 문서에 찍히는 글자 그대로.
 * itemRows: 행마다 [전자기기 품목, 수량, 가구 품목, 수량, 생활용품 품목, 수량, 기타 품목, 수량] ('' = 빈칸), 최소 8행
 * SAMPLE: 화면 미리보기·등록 화면용 예시 (견적 1번 홍길동)
 */
public record SheetView(String companyName, String customer, String phone, String estimateDate, String origin,
		String moveDate, String destination, String transportDate, List<List<String>> itemRows,
		List<OptionRow> options, String workMethod, List<TransportRow> transports, String volume, String note,
		String movingCost, String extraCost, String optionCost, String total, String fromLadderFloor,
		String departureLadder, String toLadderFloor, String destinationLadder, String ladderSubtotal,
		String deposit, String balance, String signer) {

	public record OptionRow(String name, String choice1, String choice2, boolean on1, boolean on2, String cost) {
	}

	/** name: 사다리차 · 승강기 · 계단작업, from/to: 출발(전)·도착(후) 사용 */
	public record TransportRow(String name, boolean from, boolean to) {
	}

	public static final List<String> EMPTY_ROW = List.of("", "", "", "", "", "", "", "");

	public static final SheetView SAMPLE = new SheetView("명품익스프레스", "홍길동", "010-1234-5678", "26.03.25",
			"서울 강남구 테헤란로 123, 101동 1203호", "26.04.12 08:00", "경기 성남시 분당구 판교로 45, 2층", "26.04.12",
			List.of(List.of("냉장고", "1대", "침대", "1개", "박스", "25개", "", ""),
					List.of("세탁기", "1대", "책상", "2개", "", "", "", ""),
					List.of("", "", "의자", "4개", "", "", "", ""),
					EMPTY_ROW, EMPTY_ROW, EMPTY_ROW, EMPTY_ROW, EMPTY_ROW),
			List.of(new OptionRow("피아노", "일반", "그랜드", false, false, "0"),
					new OptionRow("돌침대", "분해", "조립", false, false, "0"),
					new OptionRow("조립장", "분해", "조립", false, false, "0"),
					new OptionRow("벽걸이TV", "탈착", "설치", true, true, "20,000"),
					new OptionRow("홈씨어터", "분리", "설치", false, false, "0"),
					new OptionRow("수작업", "계단", "승강기", false, false, "0"),
					new OptionRow("안마의자", "분리", "설치", false, false, "0"),
					new OptionRow("에어컨", "탈착", "설치", false, false, "0")),
			"반포장",
			List.of(new TransportRow("사다리차", true, true), new TransportRow("승강기", false, false),
					new TransportRow("계단작업", false, false)),
			"3.5톤", "파손 주의 물품 별도 포장", "1,050,000", "270,000", "20,000", "1,340,000", "3", "150,000", "8",
			"120,000", "270,000", "134,000", "1,206,000", "김선국　(서명) / H.P　010-9445-8224");
}
