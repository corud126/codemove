package com.codemove.videoanalysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 영상 분석 프롬프트와 응답 스키마 (팀 기준 CodeMove-AI 의 estimate/AnalysisPrompt · prompt-v2 와 같은 문구 · 형식).
 * 지시문은 resources/video-analysis/prompt-{VERSION}.txt, 품목표는 기준표에서 만들어 {{CATALOG}} 자리에 넣는다.
 * 프롬프트를 바꾸면 VERSION 을 올려 실행 기록에서 버전별 결과를 비교한다.
 */
public final class AnalysisPrompt {

	/**
	 * 기본 버전. v2: 대표님 견적 방식(방 순서 · 바구니 환산 · 서랍 속 짐 · 렌탈 · 고가품 · 벽걸이 TV 인치, 팀과 같은 문구)
	 * v3: v2 + house-01 1회차에서 덜 센 것(의자 · 플라스틱 서랍 · 행거 · 선반 잔짐)을 한 개씩 세도록 보강 (우리 실험)
	 * v4: v3 + 영상 상태(quality_issues · coverage · unseen_areas)를 받아 분석이 어려운 영상을 나눠 처리
	 */
	public static final String VERSION = "v2";
	public static final List<String> VERSIONS = List.of("v2", "v3", "v4");

	private static final Map<String, String> TEMPLATES = new java.util.concurrent.ConcurrentHashMap<>();

	private AnalysisPrompt() {
	}

	public static String build(List<ItemStandard> table) {
		return build(VERSION, table);
	}

	/** 없는 버전이면 기본 버전 */
	public static String build(String version, List<ItemStandard> table) {
		return template(VERSIONS.contains(version) ? version : VERSION).replace("{{CATALOG}}", catalogTable(table));
	}

	/** 품목명 | 분류 | 01 | 02 | 03 (크기명: 판정기준) */
	static String catalogTable(List<ItemStandard> table) {
		StringBuilder sb = new StringBuilder("품목명 | 분류 | 01 | 02 | 03\n");
		Map<String, List<ItemStandard>> byName = new LinkedHashMap<>();
		table.forEach(s -> byName.computeIfAbsent(s.name(), k -> new java.util.ArrayList<>()).add(s));
		byName.forEach((name, sizes) -> {
			sb.append(name).append(" | ").append(ItemStandardCatalog.CATEGORIES.getOrDefault(sizes.get(0).category(), ""));
			sizes.stream().sorted(java.util.Comparator.comparing(ItemStandard::sizeCode)).forEach(z -> sb.append(" | ")
					.append(z.sizeName()).append(": ").append(z.guide().replace("[업체 확인]", "").trim()));
			sb.append('\n');
		});
		return sb.toString();
	}

	/** Gemini responseSchema (OpenAPI 부분집합) */
	public static Map<String, Object> responseSchema() {
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("item_name", str("품목표의 품목명 그대로, 없으면 기타"));
		props.put("size_code", Map.of("type", "STRING", "enum", List.of("01", "02", "03")));
		props.put("quantity", Map.of("type", "INTEGER"));
		props.put("detected_name", str("영상에서 본 물건 이름"));
		props.put("room", str("물건이 있는 공간"));
		props.put("evidence", str("판단 근거 한 줄"));
		props.put("needs_review", Map.of("type", "BOOLEAN"));
		props.put("inch", Map.of("type", "INTEGER", "nullable", true, "description", "TV 일 때 추정 인치"));
		props.put("wall_mounted", Map.of("type", "BOOLEAN", "description", "벽에 걸린 TV 면 true"));
		Map<String, Object> item = obj(props, List.of("item_name", "size_code", "quantity", "detected_name", "room", "needs_review"));
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("video_ok", Map.of("type", "BOOLEAN"));
		root.put("reject_reason", Map.of("type", "STRING", "nullable", true));
		root.put("rooms", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
		root.put("items", Map.of("type", "ARRAY", "items", item));
		root.put("memo", str("방문 견적자에게 남길 메모"));
		root.put("quality_issues", Map.of("type", "ARRAY", "items", Map.of("type", "STRING", "enum", List.of("어두움", "흔들림", "흐림", "너무 빠름", "가려짐"))));
		root.put("coverage", Map.of("type", "STRING", "enum", List.of("전체", "일부", "거의없음")));
		root.put("unseen_areas", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
		return obj(root, List.of("video_ok", "rooms", "items"));
	}

	private static Map<String, Object> obj(Map<String, Object> props, List<String> required) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "OBJECT");
		m.put("properties", props);
		m.put("required", required);
		return m;
	}

	private static Map<String, Object> str(String description) {
		return Map.of("type", "STRING", "description", description);
	}

	private static String template(String version) {
		return TEMPLATES.computeIfAbsent(version, v -> {
			try (InputStream in = AnalysisPrompt.class.getResourceAsStream("/video-analysis/prompt-" + v + ".txt")) {
				if (in == null) {
					throw new IllegalStateException("video-analysis/prompt-" + v + ".txt 가 없어요");
				}
				String t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				return t.startsWith("\uFEFF") ? t.substring(1) : t;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
