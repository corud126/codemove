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

	/** v2: 대표님 견적 방식(방 순서 · 바구니 환산 · 서랍 속 짐 · 렌탈 · 고가품 · 벽걸이 TV 인치) */
	public static final String VERSION = "v2";

	private static volatile String template;

	private AnalysisPrompt() {
	}

	public static String build(List<ItemStandard> table) {
		return template().replace("{{CATALOG}}", catalogTable(table));
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

	private static String template() {
		if (template == null) {
			try (InputStream in = AnalysisPrompt.class.getResourceAsStream("/video-analysis/prompt-" + VERSION + ".txt")) {
				if (in == null) {
					throw new IllegalStateException("video-analysis/prompt-" + VERSION + ".txt 가 없어요");
				}
				String t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				template = t.startsWith("﻿") ? t.substring(1) : t;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return template;
	}
}
