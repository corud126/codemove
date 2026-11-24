package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 응답(JSON 글) → 검증된 품목 목록.
 * 필수 칸(detected_name · quantity)이 잘못된 품목은 빼고 errors 에, 고칠 수 있는 문제(크기 등급 누락 등)는 warnings 에 남긴다.
 * AI 가 돌려준 신뢰도 숫자는 실제 확률이 아니므로 받지 않는다 (needs_review 만 쓴다).
 */
public final class AiResultParser {

	public static final int MAX_QUANTITY = 99;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	/**
	 * detectedName: AI 가 본 이름 · standardName: AI 가 기준표에서 고른 품목명(없을 수 있음)
	 * sizeGrade: 소형 · 중형 · 대형 (모르면 빈 문자열) · evidence: 식별 단서
	 */
	public record AiItem(String detectedName, String standardName, String category, int quantity, String sizeGrade,
			List<String> features, boolean needsReview, String evidence) {
	}

	public record Parsed(List<AiItem> items, List<String> errors, List<String> warnings) {

		public boolean ok() {
			return errors.isEmpty();
		}
	}

	private AiResultParser() {
	}

	public static Parsed parse(String raw) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<AiItem> items = new ArrayList<>();
		Map<?, ?> root;
		try {
			Object value = JSON.readValue(stripFence(raw), Object.class);
			if (!(value instanceof Map<?, ?> m)) {
				errors.add("최상위가 객체({ })가 아니에요.");
				return new Parsed(items, errors, warnings);
			}
			root = m;
		} catch (JacksonException e) {
			errors.add("JSON 형식이 아니에요: " + e.getOriginalMessage());
			return new Parsed(items, errors, warnings);
		}
		if (!(root.get("items") instanceof List<?> list)) {
			errors.add("items 배열이 없어요.");
			return new Parsed(items, errors, warnings);
		}
		for (int i = 0; i < list.size(); i++) {
			String at = (i + 1) + "번째 품목";
			if (!(list.get(i) instanceof Map<?, ?> m)) {
				errors.add(at + ": 객체가 아니에요.");
				continue;
			}
			String name = text(m.get("detected_name"));
			if (name.isEmpty()) {
				errors.add(at + ": detected_name 이 비어 있어요.");
				continue;
			}
			Integer quantity = integer(m.get("quantity"));
			if (quantity == null) {
				errors.add(at + " (" + name + "): quantity 가 정수가 아니에요.");
				continue;
			}
			if (quantity < 1 || quantity > MAX_QUANTITY) {
				errors.add(at + " (" + name + "): quantity 는 1~" + MAX_QUANTITY + " 이어야 해요 (받은 값 " + quantity + ").");
				continue;
			}
			String grade = text(m.get("size_grade"));
			boolean review = Boolean.TRUE.equals(m.get("needs_review"));
			if (ItemStandard.sizeCodeOf(grade) == null) {
				warnings.add(at + " (" + name + "): size_grade '" + grade + "' 를 알 수 없어 확인 필요로 표시했어요.");
				grade = "";
				review = true;
			}
			List<String> features = m.get("features") instanceof List<?> f
					? f.stream().map(AiResultParser::text).filter(s -> !s.isEmpty()).toList()
					: List.of();
			items.add(new AiItem(name, text(m.get("standard_name")), text(m.get("category")), quantity, grade, features,
					review, text(m.get("evidence"))));
		}
		return new Parsed(List.copyOf(items), List.copyOf(errors), List.copyOf(warnings));
	}

	/** ```json ... ``` 로 감싼 응답이면 안쪽만 */
	static String stripFence(String raw) {
		String s = raw == null ? "" : raw.trim();
		if (s.startsWith("```")) {
			int start = s.indexOf('\n');
			int end = s.lastIndexOf("```");
			if (start > 0 && end > start) {
				return s.substring(start + 1, end).trim();
			}
		}
		return s;
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	/** 정수만 (1.0 처럼 소수점 없는 실수는 허용, 1.5 는 null) */
	private static Integer integer(Object value) {
		if (value instanceof Integer i) {
			return i;
		}
		if (value instanceof Long l && l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) {
			return l.intValue();
		}
		if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
			return (int) n.doubleValue();
		}
		return null;
	}
}
