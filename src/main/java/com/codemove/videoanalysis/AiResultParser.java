package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 응답(JSON 글) → 검증된 품목 목록. 형식은 팀 기준(AI_VIDEO_ESTIMATE.md 2.1, 프롬프트 v2)과 같다:
 * { video_ok, reject_reason, rooms[], memo, items[{ item_name, size_code, quantity, detected_name, room, evidence,
 * needs_review, inch, wall_mounted }] }
 * - item_name · detected_name 이 둘 다 비었거나 quantity 가 정수가 아니면 그 품목은 빼고 errors 에 남긴다.
 * - quantity 가 1~200 밖이면 범위 안으로 자르고 확인 필요 (팀 기준). size_code 가 01~03 이 아니면 비우고 확인 필요.
 * AI 가 돌려준 신뢰도 숫자는 실제 확률이 아니므로 받지 않는다 (needs_review 만 쓴다).
 */
public final class AiResultParser {

	public static final int MIN_QUANTITY = 1;
	public static final int MAX_QUANTITY = 200;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	/**
	 * itemName: 품목표 이름 또는 "기타" · sizeCode: 01~03 (모르면 빈 문자열) · detectedName: 영상에서 본 이름
	 * inch: TV 추정 인치(없으면 null) · wallMounted: 벽걸이 TV
	 */
	public record AiItem(String itemName, String sizeCode, int quantity, String detectedName, String room, String evidence,
			boolean needsReview, Integer inch, boolean wallMounted) {
	}

	/** videoOk 가 false 면 rejectReason 에 이유 (어두움 · 집 안 영상 아님 등) */
	public record Parsed(boolean videoOk, String rejectReason, List<String> rooms, String memo, List<AiItem> items,
			List<String> errors, List<String> warnings) {

		/** 형식 자체가 깨져 품목을 하나도 못 읽은 경우 */
		public boolean broken() {
			return items.isEmpty() && !errors.isEmpty();
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
				return new Parsed(true, "", List.of(), "", items, errors, warnings);
			}
			root = m;
		} catch (JacksonException e) {
			errors.add("JSON 형식이 아니에요: " + e.getOriginalMessage());
			return new Parsed(true, "", List.of(), "", items, errors, warnings);
		}
		boolean videoOk = !Boolean.FALSE.equals(root.get("video_ok"));
		List<String> rooms = root.get("rooms") instanceof List<?> r
				? r.stream().map(AiResultParser::text).filter(s -> !s.isEmpty()).toList()
				: List.of();
		if (!(root.get("items") instanceof List<?> list)) {
			if (videoOk) {
				errors.add("items 배열이 없어요.");
			}
			return new Parsed(videoOk, text(root.get("reject_reason")), rooms, text(root.get("memo")), items, errors, warnings);
		}
		for (int i = 0; i < list.size(); i++) {
			String at = (i + 1) + "번째 품목";
			if (!(list.get(i) instanceof Map<?, ?> m)) {
				errors.add(at + ": 객체가 아니에요.");
				continue;
			}
			String name = text(m.get("item_name"));
			String detected = text(m.get("detected_name"));
			String label = name.isEmpty() ? detected : name;
			if (label.isEmpty()) {
				errors.add(at + ": item_name 과 detected_name 이 모두 비어 있어요.");
				continue;
			}
			Integer quantity = integer(m.get("quantity"));
			if (quantity == null) {
				errors.add(at + " (" + label + "): quantity 가 정수가 아니에요.");
				continue;
			}
			boolean review = Boolean.TRUE.equals(m.get("needs_review"));
			if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
				int clamped = Math.max(MIN_QUANTITY, Math.min(MAX_QUANTITY, quantity));
				warnings.add(at + " (" + label + "): quantity " + quantity + " → " + clamped + " 로 고치고 확인 필요로 표시했어요.");
				quantity = clamped;
				review = true;
			}
			String size = ItemStandard.sizeCodeOf(text(m.get("size_code")));
			if (size == null) {
				if (!text(m.get("size_code")).isEmpty()) {
					warnings.add(at + " (" + label + "): size_code '" + text(m.get("size_code")) + "' 를 알 수 없어요.");
				}
				size = "";
			}
			items.add(new AiItem(name, size, quantity, detected, text(m.get("room")), text(m.get("evidence")), review,
					integer(m.get("inch")), Boolean.TRUE.equals(m.get("wall_mounted"))));
		}
		return new Parsed(videoOk, text(root.get("reject_reason")), rooms, text(root.get("memo")), List.copyOf(items),
				List.copyOf(errors), List.copyOf(warnings));
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
		if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
				&& Math.abs(n.doubleValue()) <= Integer.MAX_VALUE) {
			return (int) n.doubleValue();
		}
		return null;
	}
}
