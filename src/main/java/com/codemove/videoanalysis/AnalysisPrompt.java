package com.codemove.videoanalysis;

import java.util.List;
import java.util.Map;

/**
 * 영상 분석 프롬프트 (버전을 올리면 VERSION 도 바꾼다: 실행 기록에서 프롬프트별 결과를 비교한다).
 * 기준표의 품목명 · 크기 판정기준을 함께 보내 AI 가 표준 품목명(standard_name)을 직접 고르게 한다.
 */
public final class AnalysisPrompt {

	public static final String VERSION = "v1";

	private AnalysisPrompt() {
	}

	public static String build(List<ItemStandard> table) {
		StringBuilder sb = new StringBuilder("""
				너는 이사 견적용 짐 분석기다. 집 내부를 찍은 영상을 보고 옮겨야 할 물품을 찾아 JSON 으로만 답한다.

				규칙
				1. 영상에 실제로 보이는 물품만 적는다. 보이지 않는 물품을 추측해서 넣지 않는다.
				2. 같은 물건이 여러 장면에 반복해서 나와도 한 번만 센다. 같은 종류가 여러 개면 quantity 로 센다.
				3. standard_name 은 아래 [품목 기준표]의 품목명 중 하나를 글자 그대로 쓴다. 맞는 품목이 없으면 빈 문자열로 둔다.
				4. size_grade 는 소형 · 중형 · 대형 중 하나다. 기준표의 판정기준을 보고 정한다.
				5. 옷 · 책 · 잡화 같은 잔짐은 하나씩 세지 말고 standard_name "이사박스" 로, 이사 박스 몇 개 분량인지 quantity 에 적는다.
				6. 크기나 종류가 확실하지 않거나, 가려져서 일부만 보이면 needs_review 를 true 로 한다.
				7. evidence 에는 그렇게 판단한 단서(보이는 모양, 문 개수, 대략 크기 등)를 짧게 적는다.
				8. category 는 가구 · 가전 · 생활용품 · 기타 중 하나다.

				[품목 기준표] 품목명 | 카테고리 | 크기: 판정기준
				""");
		Map<String, String> categories = ItemStandardCatalog.CATEGORIES;
		String last = null;
		for (ItemStandard s : table) {
			if (!s.name().equals(last)) {
				sb.append("\n- ").append(s.name()).append(" | ").append(categories.getOrDefault(s.category(), s.category()));
				last = s.name();
			}
			sb.append(" | ").append(ItemStandard.sizeLabel(s.sizeCode()));
			if (!s.sizeName().isEmpty() || !s.guide().isEmpty()) {
				sb.append(": ").append(s.sizeName());
				if (!s.guide().isEmpty()) {
					sb.append(" (").append(s.guide()).append(')');
				}
			}
		}
		return sb.append('\n').toString();
	}

	/** Gemini responseSchema (OpenAPI 부분집합): 응답 모양을 고정한다 */
	public static Map<String, Object> responseSchema() {
		Map<String, Object> item = Map.of(
				"type", "OBJECT",
				"properties", Map.of(
						"detected_name", Map.of("type", "STRING"),
						"standard_name", Map.of("type", "STRING"),
						"category", Map.of("type", "STRING", "enum", List.of("가구", "가전", "생활용품", "기타")),
						"quantity", Map.of("type", "INTEGER"),
						"size_grade", Map.of("type", "STRING", "enum", List.of("소형", "중형", "대형")),
						"features", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
						"needs_review", Map.of("type", "BOOLEAN"),
						"evidence", Map.of("type", "STRING")),
				"required", List.of("detected_name", "standard_name", "category", "quantity", "size_grade", "needs_review"),
				"propertyOrdering", List.of("detected_name", "standard_name", "category", "quantity", "size_grade", "features",
						"needs_review", "evidence"));
		return Map.of("type", "OBJECT", "properties", Map.of("items", Map.of("type", "ARRAY", "items", item)),
				"required", List.of("items"));
	}
}
