package com.codemove.videoanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.codemove.videoanalysis.AiResultParser.AiItem;
import com.codemove.videoanalysis.ItemMappingService.MappedItem;
import com.codemove.videoanalysis.ItemMappingService.Type;
import com.codemove.videoanalysis.ItemStandardCatalog.Alias;
import com.codemove.videoanalysis.ItemStandardCatalog.Truck;

/** 영상 분석의 AI 이후 단계(기준 데이터 · JSON 검증 · 매핑 · 부피/무게 · 톤 · 정답 비교) 단위 테스트. 스프링 · DB · 외부 API 없이 */
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

	static final List<Alias> ALIASES = ItemStandardCatalog.parseAliases(
			"별칭,품목명,크기코드,비고\n양문형 냉장고,냉장고,03,\n김치냉장고,김치냉장고,,\n냉장고,냉장고,,\n거실장,TV장,,\n식탁,식탁·테이블,,\n바구니,이사박스,02,\n박스,이사박스,,\n티비,TV,,\n");

	static final List<Truck> TRUCKS = ItemStandardCatalog.parseTrucks("톤,적재CBM\n5.0,35\n1.0,10\n2.5,20\n3.5,25\n");

	static final ItemStandardCatalog.Snapshot BASE = ItemStandardCatalog.parse(CSV, "test");
	static final ItemStandardCatalog.Snapshot TABLE = new ItemStandardCatalog.Snapshot(BASE.list(), BASE.byKey(), ALIASES, TRUCKS,
			"test", BASE.loadedAt(), null);

	static AiItem ai(String itemName, String size, int quantity, String detected) {
		return new AiItem(itemName, size, quantity, detected, "", "", false, null, false);
	}

	static AiItem ai(String itemName, String size, int quantity, String detected, String room) {
		return new AiItem(itemName, size, quantity, detected, room, "", false, null, false);
	}

	@Test
	void csvParsing() {
		assertThat(TABLE.ready()).isTrue();
		assertThat(TABLE.list()).hasSize(7); // 크기코드 09 행은 건너뜀
		assertThat(TABLE.names()).containsExactly("냉장고", "김치냉장고", "식탁·테이블", "TV", "TV장", "이사박스");
		ItemStandard fridge = TABLE.find("냉장고", "02");
		assertThat(fridge.guide()).isEqualTo("위아래 2도어, 300L 안팎");
		assertThat(fridge.weightKg()).isEqualByComparingTo("60.5");
		assertThat(TABLE.find("냉장고", "03").weightKg()).isNull();
		assertThat(ALIASES).hasSize(8);
		assertThat(ALIASES.get(0).sizeCode()).isEqualTo("03");
		assertThat(TRUCKS).extracting(Truck::ton).containsExactly(new BigDecimal("1.0"), new BigDecimal("2.5"),
				new BigDecimal("3.5"), new BigDecimal("5.0")); // 톤 순으로 정렬
	}

	@Test
	void parserTeamFormat() {
		AiResultParser.Parsed p = AiResultParser.parse("""
				```json
				{"video_ok":true,"rooms":["주방","거실"],"memo":"정수기 렌탈 확인",
				 "items":[
				 {"item_name":"냉장고","size_code":"03","quantity":1,"detected_name":"4도어 냉장고","room":"주방","needs_review":false},
				 {"item_name":"","size_code":"01","quantity":1,"detected_name":"","room":"거실","needs_review":false},
				 {"item_name":"소파","size_code":"02","quantity":1.5,"room":"거실","needs_review":false},
				 {"item_name":"이사박스","size_code":"02","quantity":250,"detected_name":"잔짐","room":"팬트리","needs_review":false},
				 {"item_name":"TV","size_code":"특대","quantity":1,"detected_name":"벽걸이 TV","room":"거실","needs_review":false,"inch":65,"wall_mounted":true}
				]}
				```""");
		assertThat(p.videoOk()).isTrue();
		assertThat(p.rooms()).containsExactly("주방", "거실");
		assertThat(p.memo()).isEqualTo("정수기 렌탈 확인");
		assertThat(p.items()).extracting(AiItem::itemName).containsExactly("냉장고", "이사박스", "TV");
		assertThat(p.errors()).hasSize(2); // 이름 없음 · 수량 1.5
		AiItem box = p.items().get(1);
		assertThat(box.quantity()).isEqualTo(200); // 1~200 으로 잘라 냄 (팀 기준)
		assertThat(box.needsReview()).isTrue();
		AiItem tv = p.items().get(2);
		assertThat(tv.sizeCode()).isEmpty();
		assertThat(tv.inch()).isEqualTo(65);
		assertThat(tv.wallMounted()).isTrue();
		assertThat(p.warnings()).hasSize(2);

		AiResultParser.Parsed rejected = AiResultParser.parse("{\"video_ok\":false,\"reject_reason\":\"너무 어두움\",\"rooms\":[],\"items\":[]}");
		assertThat(rejected.videoOk()).isFalse();
		assertThat(rejected.rejectReason()).isEqualTo("너무 어두움");
		assertThat(rejected.broken()).isFalse();
		assertThat(AiResultParser.parse("이건 JSON 이 아님").broken()).isTrue();
		assertThat(AiResultParser.parse("{\"list\":[]}").errors()).containsExactly("items 배열이 없어요.");
	}

	@Test
	void mappingOrderFollowsTeam() {
		List<MappedItem> m = ItemMappingService.map(List.of(
				ai("냉장고", "03", 1, "4도어 냉장고"), // 01 정확 일치
				ai("냉장고", "", 1, "냉장고"), // 품목은 맞는데 크기 없음 → 03 AI 후보, 중형
				ai("기타", "", 1, "양문형 냉장고"), // 본 이름이 별칭과 같음 → 02, 별칭 크기 03
				ai("기타", "02", 1, "스탠드 김치냉장고"), // 포함된 가장 긴 별칭 김치냉장고(냉장고보다 우선)
				ai("거실장", "02", 1, "원목 거실장"), // AI 이름이 별칭 → TV장
				ai("기타", "02", 13, "플라스틱 바구니"), // 별칭 크기 02
				ai("기타", "", 1, "빨래 건조대")), TABLE); // 04 미매핑

		assertThat(m).extracting(MappedItem::type).containsExactly(Type.EXACT, Type.AI_GUESS, Type.ALIAS, Type.ALIAS, Type.ALIAS,
				Type.ALIAS, Type.UNMAPPED);
		assertThat(m).extracting(MappedItem::standardName).containsExactly("냉장고", "냉장고", "냉장고", "김치냉장고", "TV장", "이사박스", "");
		assertThat(m).extracting(MappedItem::sizeCode).containsExactly("03", "02", "03", "02", "02", "02", "02");
		assertThat(m.get(0).needsReview()).isFalse();
		assertThat(m.get(1).needsReview()).isTrue();
		assertThat(m.get(6).standard()).isNull();
		assertThat(m.get(6).reasons()).anyMatch(r -> r.contains("기타"));
		assertThat(Type.EXACT.code()).isEqualTo("01");
		assertThat(Type.UNMAPPED.code()).isEqualTo("04");
	}

	@Test
	void ambiguousAliasIsNotForced() {
		// 같은 길이로 서로 다른 품목을 가리키면 고르지 않는다 (우리 쪽 추가 규칙)
		List<String> c = ItemMappingService.aliasCandidates("티비 식탁", ALIASES,
				Map.of("tv", "TV", "식탁테이블", "식탁·테이블"));
		assertThat(c).containsExactlyInAnyOrder("TV|", "식탁·테이블|");
		MappedItem m = ItemMappingService.map(List.of(ai("기타", "02", 1, "티비 식탁")), TABLE).get(0);
		assertThat(m.type()).isEqualTo(Type.UNMAPPED);
		assertThat(m.candidates()).containsExactlyInAnyOrder("TV", "식탁·테이블");
	}

	@Test
	void duplicateOnlyWithinSameRoom() {
		List<MappedItem> m = ItemMappingService.map(List.of(ai("냉장고", "02", 1, "", "주방"), ai("냉장고", "02", 1, "", "베란다"),
				ai("냉장고", "02", 1, "", "주방")), TABLE);
		assertThat(m.get(1).needsReview()).isFalse(); // 방이 다르면 따로 적는 게 규칙
		assertThat(m.get(2).reasons()).anyMatch(x -> x.contains("같은 공간"));
	}

	@Test
	void volumeAndWeight() {
		List<MappedItem> m = ItemMappingService.map(List.of(
				ai("냉장고", "02", 2, ""), // 0.680 × 2 = 1.360 ㎥, 60.5 × 2 = 121.0 kg
				ai("냉장고", "03", 1, ""), // 1.490 ㎥, 무게 없음
				ai("이사박스", "02", 13, ""), // 0.077 × 13 = 1.001 ㎥, 무게 없음
				ai("기타", "", 1, "러닝머신")), TABLE); // 미매핑 → 계산 제외
		VolumeWeightService.Result r = new VolumeWeightService().calculate(m);
		assertThat(r.lines().get(0).totalCbm()).isEqualByComparingTo("1.360");
		assertThat(r.lines().get(0).totalKg()).isEqualByComparingTo("121.0");
		assertThat(r.lines().get(3).totalCbm()).isNull();
		assertThat(r.totalCbm()).isEqualByComparingTo("3.851");
		assertThat(r.totalKg()).isEqualByComparingTo("121.0");
		assertThat(r.cbmMissing()).isEqualTo(1);
		assertThat(r.kgMissing()).isEqualTo(2);
		assertThat(new VolumeWeightService().calculate(m)).isEqualTo(r); // 같은 입력 → 같은 결과
	}

	@Test
	void tonsFollowOwnerRule() {
		BigDecimal f = new BigDecimal("2.0");
		// 대표님 댁: 물품 27.5 × 2.0 = 55 → 5톤 + 1톤 + 1톤 = 7톤
		TruckLoadService.Load home = TruckLoadService.estimate(new BigDecimal("27.5"), TRUCKS, f);
		assertThat(home.loadCbm()).isEqualByComparingTo("55");
		assertThat(home.ton()).isEqualByComparingTo("7");
		assertThat(home.label()).isEqualTo("5톤 + 1톤 × 2");
		// 적재 8 → 1톤, 15 → 1톤 2대(2톤) < 2.5톤, 22 → 3.5톤 (1톤 2대로 안 들어감), 70 → 5톤 2대
		assertThat(TruckLoadService.estimate(new BigDecimal("4"), TRUCKS, f).ton()).isEqualByComparingTo("1");
		assertThat(TruckLoadService.estimate(new BigDecimal("7.5"), TRUCKS, f).label()).isEqualTo("1톤 × 2");
		assertThat(TruckLoadService.estimate(new BigDecimal("11"), TRUCKS, f).ton()).isEqualByComparingTo("3.5");
		assertThat(TruckLoadService.estimate(new BigDecimal("35"), TRUCKS, f).label()).isEqualTo("5톤 × 2");
		assertThat(TruckLoadService.estimate(BigDecimal.ONE, List.of(), f)).isNull();
	}

	@Test
	void groundTruthScore() {
		List<GroundTruthScorer.Line> truth = GroundTruthScorer.lines(
				"공간,품목명,크기코드,수량,본 물건\n주방,냉장고,03,1,x\n팬트리,이사박스,02,12,x\n거실,기타,,1,건조대\n거실,TV,02,1,x\n");
		List<MappedItem> ai = ItemMappingService.map(List.of(ai("냉장고", "03", 1, ""), ai("이사박스", "02", 10, ""),
				ai("김치냉장고", "02", 1, ""), ai("기타", "", 2, "반려동물 하우스")), TABLE);
		GroundTruthScorer.Score s = GroundTruthScorer.score("t", truth, ai, TABLE);
		// 정답 14개(냉장고 1 + 박스 12 + TV 1), AI 12개(냉장고 1 + 박스 10 + 김치냉장고 1), 일치 11
		assertThat(s.expectedPieces()).isEqualTo(14);
		assertThat(s.detectedPieces()).isEqualTo(12);
		assertThat(s.matchedPieces()).isEqualTo(11);
		assertThat(s.precision()).isEqualByComparingTo("0.917");
		assertThat(s.recall()).isEqualByComparingTo("0.786");
		assertThat(s.f1()).isEqualByComparingTo("0.846");
		assertThat(s.expectedOther()).isEqualTo(1);
		assertThat(s.detectedOther()).isEqualTo(2);
		// CBM: 정답 1.490 + 0.924 + 0.280 = 2.694, AI 1.490 + 0.770 + 0.840 = 3.100 → +15.1%
		assertThat(s.expectedCbm()).isEqualByComparingTo("2.694");
		assertThat(s.detectedCbm()).isEqualByComparingTo("3.100");
		assertThat(s.cbmErrorPct()).isEqualByComparingTo("15.1");
	}

	@Test
	void promptHasCatalogTable() {
		String p = AnalysisPrompt.build(TABLE.list());
		assertThat(p).contains("품목명 | 분류 | 01 | 02 | 03").contains("냉장고 | 가전 | 일반 2도어: 위아래 2도어, 300L 안팎 | 양문형: 좌우로 열림")
				.doesNotContain("{{CATALOG}}");
		assertThat(AnalysisPrompt.responseSchema().toString()).contains("video_ok", "item_name", "size_code", "wall_mounted");
	}

	@Test
	void priceIsChosenByRespondingModelVersion() {
		var hydrogen = new VideoAnalysisProperties.ModelPrice(new BigDecimal("1.50"), new BigDecimal("7.50"));
		var flash = new VideoAnalysisProperties.ModelPrice(new BigDecimal("0.75"), new BigDecimal("3.75"));
		var price = new VideoAnalysisProperties.Price(null, null,
				Map.of("gemini-4-hydrogen", hydrogen, "gemini-3.6-flash", flash), null, "2026-11-24", null);
		assertThat(price.find("gemini-3.5-flash", "gemini-3.6-flash")).isEqualTo(flash); // 응답 버전 우선
		assertThat(price.find("gemini-4-hydrogen", "")).isEqualTo(hydrogen); // 버전이 없으면 요청 모델
		assertThat(price.find("other", "other-1")).isNull(); // 모르면 단가 미설정
		assertThat(new VideoAnalysisProperties.Price(null, null, null, null, null, null).set()).isFalse();
	}

	/** 판정 테스트용 실행 기록: errorCode · 영상 상태 · 공간 · 품목 */
	static AnalysisRun run(String errorCode, AiResultParser.VideoState state, List<String> rooms, List<AiItem> items, String reject) {
		VolumeWeightService.Result result = errorCode == null ? new VolumeWeightService().calculate(ItemMappingService.map(items, TABLE))
				: null;
		return new AnalysisRun("20261124-000000-000", null, null, "GEMINI", errorCode == null, errorCode, null, null, "m", "", "",
				null, "v4", null, null, null, 0, "", new AnalysisRun.Ai(errorCode == null || !"03".equals(errorCode), reject, rooms, "",
						state), List.of(), List.of(), result, null, null);
	}

	@Test
	void outcomeForUnreadableVideos() {
		AnalysisOutcomePolicy policy = new AnalysisOutcomePolicy(2, 3, 0.5);
		var dark = new AiResultParser.VideoState(List.of("어두움"), "거의없음", List.of());
		// 03 어두움 → 다시 찍기 + 촬영 요령, 2회까지
		var o = policy.evaluate(run("03", dark, List.of(), List.of(), "너무 어두움"), 1);
		assertThat(o.type()).isEqualTo(AnalysisOutcomePolicy.Type.RETAKE);
		assertThat(o.title()).contains("어두워서");
		assertThat(o.tips()).isNotEmpty();
		assertThat(o.actions().get(0).key()).isEqualTo("retake");
		assertThat(o.attemptsLeft()).isEqualTo(2);
		// 3번째 시도까지 실패 → 다시 찍기 없음, 직접 입력 · 방문 견적만
		var last = policy.evaluate(run("03", dark, List.of(), List.of(), ""), 3);
		assertThat(last.attemptsLeft()).isZero();
		assertThat(last.actions()).extracting(AnalysisOutcomePolicy.Action::key).containsExactly("manual", "visit");
		// 흔들림 제목 · 04 집 아님
		assertThat(AnalysisOutcomePolicy.qualityTitle(List.of("흔들림"), "")).contains("흔들려서");
		assertThat(policy.evaluate(run("04", AiResultParser.VideoState.NONE, List.of(), List.of(), "집 안 아님"), 1).title())
				.contains("짐이 잘 보이지");
		// AI 쪽 문제(10 혼잡) → 잠시 후 다시, 영상은 다시 올리지 않음
		var busy = policy.evaluate(run("10", AiResultParser.VideoState.NONE, List.of(), List.of(), ""), 1);
		assertThat(busy.type()).isEqualTo(AnalysisOutcomePolicy.Type.RETRY_LATER);
		assertThat(busy.actions().get(0).key()).isEqualTo("reanalyze");
		// 업로드에서 막힘(02)
		assertThat(policy.forUploadReject("02", "5초~5분", 1).type()).isEqualTo(AnalysisOutcomePolicy.Type.RETAKE);
	}

	@Test
	void outcomeForPartialAndOk() {
		AnalysisOutcomePolicy policy = new AnalysisOutcomePolicy(2, 3, 0.5);
		List<AiItem> many = List.of(ai("냉장고", "03", 1, "", "주방"), ai("TV", "02", 1, "", "거실"), ai("이사박스", "02", 5, "", "주방"));
		// 정상: 공간 2곳 이상 · 품목 3줄 이상 · 확인 필요 적음
		var ok = policy.evaluate(run(null, new AiResultParser.VideoState(List.of(), "전체", List.of()), List.of("주방", "거실"), many, ""), 1);
		assertThat(ok.type()).isEqualTo(AnalysisOutcomePolicy.Type.OK);
		// AI 가 일부만 봤다고 답함 → 일부만 보임, 안 보인 공간 안내
		var part = policy.evaluate(run(null, new AiResultParser.VideoState(List.of(), "일부", List.of("작은방")), List.of("주방", "거실"),
				many, ""), 1);
		assertThat(part.type()).isEqualTo(AnalysisOutcomePolicy.Type.PARTIAL);
		assertThat(part.reason()).isEqualTo("P1");
		assertThat(part.message()).contains("작은방");
		assertThat(part.actions().get(0).key()).isEqualTo("add-video");
		// 품목 1줄 · 공간 1곳 → P2 · P3
		var few = policy.evaluate(run(null, AiResultParser.VideoState.NONE, List.of("거실"), List.of(ai("TV", "02", 1, "", "거실")), ""), 1);
		assertThat(few.reason()).isEqualTo("P2,P3");
	}

	@Test
	void runIdIsPathSafe() {
		assertThat(AnalysisRunStore.safe("20261124-101500-123")).isTrue();
		assertThat(AnalysisRunStore.safe("../../etc")).isFalse();
		assertThat(AnalysisRunStore.safe(null)).isFalse();
	}
}
