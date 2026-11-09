package com.codemove.quote;

import java.util.List;
import java.util.Map;

import com.codemove.quote.SheetView.OptionRow;
import com.codemove.quote.SheetView.TransportRow;

/**
 * 견적서 등록(SCR-A-003/004) 표시값. 값은 화면 칸에 들어갈 글자 그대로.
 * values 키: companyName · customer · phone · fromAddress · toAddress · estimateDate · workDate · transportDate · workMethod
 *   · volume · note · movingCost · fromLadderFloor · fromLadderCost · toLadderFloor · toLadderCost · optionSum · total · deposit · balance
 *   · pdfName · pdfPhone · pdfEstimateDate · pdfTotal · registerName · registerLine(등록 단계 '견적일 26.09.04 · 1,420,000원')
 * fileUrl: 첨부 원본 주소 (미리보기면 null), next: 읽는 중 다음 단계 (review · unrecognized)
 * SAMPLE: 미리보기(/dev)용 예시 (김민준 사진 견적서)
 */
public record RegisterView(String source, String fileName, String fileUrl, String next, Map<String, String> values,
		List<TransportRow> transports, List<ItemGroup> groups, List<OptionRow> options) {

	/** 품목 그룹 (전자기기 · 가구 · 생활용품 · 기타 고정) */
	public record ItemGroup(String name, List<Item> items) {
	}

	public record Item(String name, String quantity, String unit) {
	}

	/** 회원 연결 시트 행: joined 는 YY.MM.DD */
	public record MemberChoice(Long id, String name, String phone, String joined) {
	}

	public static final List<MemberChoice> SAMPLE_MEMBERS = List.of(new MemberChoice(null, "김민준", "010-4821-7735", "24.05.12"),
			new MemberChoice(null, "김민서", "010-3302-1184", "25.01.07"));

	public static final RegisterView SAMPLE = new RegisterView("photo", "photo.jpg", null, "review", Map.ofEntries(
			Map.entry("companyName", "명품익스프레스"), Map.entry("customer", "김민준"), Map.entry("phone", "010-4821-7735"),
			Map.entry("fromAddress", "광주 북구 용봉로 77, 3층"), Map.entry("toAddress", "광주 서구 상무중앙로 110, 12층"),
			Map.entry("estimateDate", "26.09.04"), Map.entry("workDate", "26.09.20"), Map.entry("transportDate", "26.09.20"),
			Map.entry("workMethod", "포장"), Map.entry("volume", "3.5"), Map.entry("note", "파손 주의 물품 별도 포장"),
			Map.entry("movingCost", "1,100,000"), Map.entry("fromLadderFloor", "3"), Map.entry("fromLadderCost", "120,000"),
			Map.entry("toLadderFloor", "12"), Map.entry("toLadderCost", "150,000"), Map.entry("optionSum", "50,000"),
			Map.entry("total", "1,420,000"), Map.entry("deposit", "142,000"), Map.entry("balance", "1,278,000"),
			Map.entry("pdfName", ""), Map.entry("pdfPhone", ""), Map.entry("pdfEstimateDate", ""), Map.entry("pdfTotal", ""),
			Map.entry("registerName", "김민준"), Map.entry("registerLine", "견적일 26.09.04 · 1,420,000원")),
			List.of(new TransportRow("사다리차", true, true), new TransportRow("엘리베이터", false, false),
					new TransportRow("계단", false, false)),
			List.of(new ItemGroup("전자기기", List.of(new Item("모니터", "6", "대"), new Item("컴퓨터", "3", "대"),
					new Item("공기청정기", "1", "대"), new Item("소형 냉장고", "1", "대"), new Item("전자레인지", "1", "대"),
					new Item("프린터/복합기", "1", "대"), new Item("TV", "1", "대"))),
					new ItemGroup("가구", List.of(new Item("의자", "6", "개"), new Item("테이블", "2", "개"),
							new Item("원형 테이블", "1", "개"), new Item("파티션", "3", "개"), new Item("대형 캐비넷", "2", "개"),
							new Item("책상", "2", "개"), new Item("데스크 수납함", "3", "개"), new Item("보조 서랍장", "3", "개"))),
					new ItemGroup("생활용품", List.of()), new ItemGroup("기타", List.of())),
			List.of(new OptionRow("피아노", "일반", "그랜드", false, false, ""), new OptionRow("돌침대", "분해", "조립", false, false, ""),
					new OptionRow("조립장", "분해", "조립", false, false, ""), new OptionRow("벽걸이TV", "탈착", "설치", true, true, "50,000"),
					new OptionRow("홈씨어터", "분리", "설치", false, false, ""), new OptionRow("수작업", "계단", "승강기", false, false, ""),
					new OptionRow("안마의자", "분리", "설치", false, false, ""), new OptionRow("에어컨", "탈착", "설치", false, false, "")));
}
