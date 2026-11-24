package com.codemove.videoanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.codemove.videoanalysis.AiResultParser.AiItem;
import com.codemove.videoanalysis.ItemMappingService.MappedItem;
import com.codemove.videoanalysis.ItemMappingService.Method;

/** 영상 분석의 AI 이후 단계(기준표 읽기 · JSON 검증 · 매핑 · 부피/무게 계산) 단위 테스트. 스프링 · DB · 외부 API 없이 */
class VideoAnalysisUnitTest {

	/** 판정기준에 쉼표가 든 따옴표 칸, BOM, 무게 빈칸, 크기 없는 행을 섞은 기준표 */
	static final String CSV = "﻿품목명,카테고리,크기코드,크기명,판정기준,제안CBM,무게kg,옵션코드,근거\r\n"
			+ "냉장고,02,02,일반 2도어,\"위아래 2도어, 300L 안팎\",0.680,60.5,,조사\r\n"
			+ "냉장고,02,03,양문형,\"좌우로 열림\",1.490,,,조사\r\n"
			+ "김치냉장고,02,02,스탠드,\"세운 형태\",0.840,70,,조사\r\n"
			+ "식탁·테이블,01,02,4인,\"의자 4개\",0.720,25,,가정\r\n"
			+ "TV,02,02,55인치,\"\",0.280,15,,조사\r\n"
			+ "TV장,01,02,중형,\"\",0.405,30,,가정\r\n"
			+ "이사박스,03,02,바구니,\"\",0.077,,,가정\r\n"
			+ "잘못된행,01,09,?,\"\",1,,,\r\n";

	static final ItemStandardCatalog.Snapshot TABLE = ItemStandardCatalog.parse(CSV, "test");

	static AiItem ai(String detected, String standard, int quantity, String grade) {
		return new AiItem(detected, standard, "", quantity, grade, List.of(), false, "");
	}

	@Test
	void csvParsing() {
		assertThat(TABLE.ready()).isTrue();
		assertThat(TABLE.list()).hasSize(7); // 크기코드 09 행은 건너뜀
		assertThat(TABLE.names()).containsExactly("냉장고", "김치냉장고", "식탁·테이블", "TV", "TV장", "이사박스");
		ItemStandard fridge = TABLE.byKey().get("냉장고|02");
		assertThat(fridge.guide()).isEqualTo("위아래 2도어, 300L 안팎");
		assertThat(fridge.cbm()).isEqualByComparingTo("0.680");
		assertThat(fridge.weightKg()).isEqualByComparingTo("60.5");
		assertThat(TABLE.byKey().get("냉장고|03").weightKg()).isNull();
	}

	@Test
	void parserValidation() {
		AiResultParser.Parsed p = AiResultParser.parse("""
				```json
				{"items":[
				 {"detected_name":"냉장고","quantity":1,"size_grade":"대형"},
				 {"detected_name":"","quantity":1,"size_grade":"소형"},
				 {"detected_name":"소파","quantity":0,"size_grade":"중형"},
				 {"detected_name":"의자","quantity":1.5,"size_grade":"중형"},
				 {"detected_name":"책상","quantity":2.0,"size_grade":"특대"}
				]}
				```""");
		assertThat(p.items()).extracting(AiItem::detectedName).containsExactly("냉장고", "책상");
		assertThat(p.errors()).hasSize(3);
		AiItem desk = p.items().get(1);
		assertThat(desk.quantity()).isEqualTo(2);
		assertThat(desk.sizeGrade()).isEmpty();
		assertThat(desk.needsReview()).isTrue();
		assertThat(p.warnings()).hasSize(1);

		assertThat(AiResultParser.parse("이건 JSON 이 아님").ok()).isFalse();
		assertThat(AiResultParser.parse("{\"list\":[]}").errors()).containsExactly("items 배열이 없어요.");
	}

	@Test
	void mappingOrder() {
		List<MappedItem> m = ItemMappingService.map(List.of(
				ai("양문형 냉장고", "냉장고", 1, "대형"), // AI 가 고른 기준표 이름
				ai("TV", "", 1, "중형"), // 이름 일치
				ai("스탠드 김치냉장고", "", 1, "중형"), // 별칭: 김치냉장고(더 긴 별칭)가 냉장고보다 우선
				ai("거실장", "", 1, "중형"), // 별칭 → TV장
				ai("원목 식탁", "", 1, "중형"), // 별칭 → 식탁·테이블
				ai("러닝머신", "", 1, "대형"), // 기준표에 운동기구가 없으면 매핑 실패
				ai("TV", "없는품목", 1, "소형")), TABLE); // 이름은 맞지만 소형 규격이 없음

		assertThat(m).extracting(MappedItem::method).containsExactly(Method.STANDARD, Method.EXACT, Method.ALIAS, Method.ALIAS,
				Method.ALIAS, Method.UNMAPPED, Method.EXACT);
		assertThat(m).extracting(MappedItem::standardName).containsExactly("냉장고", "TV", "김치냉장고", "TV장", "식탁·테이블", "",
				"TV");
		assertThat(m.get(0).standard().cbm()).isEqualByComparingTo("1.490");
		assertThat(m.get(5).needsReview()).isTrue();
		assertThat(m.get(6).standard()).isNull();
		assertThat(m.get(6).reasons()).anyMatch(r -> r.contains("소형 규격이 없어요"));
	}

	@Test
	void ambiguousAliasIsNotForced() {
		// '냉장고 옆 TV' 는 같은 길이(3글자)로 걸리는 별칭이 없어도, 서로 다른 품목 두 개가 같은 최장 길이면 복수 후보
		List<String> candidates = ItemMappingService.aliasCandidates("냉장고 tv장",
				java.util.Map.of("냉장고", "냉장고", "tv장", "TV장"));
		assertThat(candidates).containsExactlyInAnyOrder("냉장고", "TV장");
		MappedItem m = ItemMappingService.map(List.of(ai("냉장고 TV장", "", 1, "중형")), TABLE).get(0);
		assertThat(m.method()).isEqualTo(Method.AMBIGUOUS);
		assertThat(m.standard()).isNull();
	}

	@Test
	void missingSizeDefaultsToMediumWithReview() {
		MappedItem m = ItemMappingService.map(List.of(ai("냉장고", "", 1, "")), TABLE).get(0);
		assertThat(m.sizeCode()).isEqualTo("02");
		assertThat(m.needsReview()).isTrue();
	}

	@Test
	void volumeAndWeight() {
		List<MappedItem> m = ItemMappingService.map(List.of(
				ai("냉장고", "", 2, "중형"), // 0.680 × 2 = 1.360 ㎥, 60.5 × 2 = 121.0 kg
				ai("냉장고", "", 1, "대형"), // 1.490 ㎥, 무게 없음
				ai("이사박스", "", 13, "중형"), // 0.077 × 13 = 1.001 ㎥, 무게 없음
				ai("러닝머신", "", 1, "대형"), // 매핑 실패 → 계산 제외
				ai("냉장고", "", 1, "중형")), TABLE); // 첫 줄과 같은 품목 · 크기 → 확인 필요(합치지 않음)
		VolumeWeightService.Result r = new VolumeWeightService().calculate(m);

		assertThat(r.lines().get(0).totalCbm()).isEqualByComparingTo("1.360");
		assertThat(r.lines().get(0).totalKg()).isEqualByComparingTo("121.0");
		assertThat(r.lines().get(2).totalCbm()).isEqualByComparingTo("1.001");
		assertThat(r.lines().get(3).totalCbm()).isNull();
		// 합계 = 반올림된 줄 합계의 합: 1.360 + 1.490 + 1.001 + 0.680
		assertThat(r.totalCbm()).isEqualByComparingTo("4.531");
		assertThat(r.totalKg()).isEqualByComparingTo("181.5");
		assertThat(r.cbmMissing()).isEqualTo(1);
		assertThat(r.kgMissing()).isEqualTo(2);
		assertThat(m.get(4).reasons()).anyMatch(x -> x.contains("같은 품목"));
		assertThat(r.reviewCount()).isEqualTo(2);
		assertThat(r.totalCbm().scale()).isEqualTo(VolumeWeightService.CBM_SCALE);

		// 같은 입력이면 같은 결과
		assertThat(new VolumeWeightService().calculate(m)).isEqualTo(r);
		assertThat(new VolumeWeightService().calculate(List.of()).totalCbm()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void runIdIsPathSafe() {
		assertThat(AnalysisRunStore.safe("20261124-101500-123")).isTrue();
		assertThat(AnalysisRunStore.safe("../../etc")).isFalse();
		assertThat(AnalysisRunStore.safe(null)).isFalse();
	}
}
