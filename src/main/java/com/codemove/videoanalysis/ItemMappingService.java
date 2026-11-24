package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.codemove.videoanalysis.AiResultParser.AiItem;
import com.codemove.videoanalysis.ItemStandardCatalog.Alias;

/**
 * AI 인식 결과 → 표준 품목 · 규격 매핑 (팀 기준 ItemMatcher 와 같은 순서, 코드는 공통코드 MAPPING_TYPE).
 * 1. item_name 이 품목표 이름 그대로 → 01 정확 일치 (크기 코드가 없으면 중형으로 두고 03 AI 후보)
 * 2. item_name → detected_name 순서로 별칭 찾기 (공백 무시 같은 이름, 없으면 포함된 가장 긴 별칭) → 02 별칭
 *    별칭에 크기가 있으면 그 크기, 없으면 AI 크기, 그것도 없으면 중형 + 확인 필요
 * 3. 못 찾으면 → 04 미매핑 (부피 0, 확인 필요)
 * 팀 기준에 더한 것: 같은 길이의 별칭이 서로 다른 품목을 가리키면 고르지 않고 미매핑(복수 후보), 같은 방 · 품목 · 크기 중복 표시.
 */
@Service
public class ItemMappingService {

	/** 공통코드 MAPPING_TYPE (05 사용자 수정은 테스트 화면에서는 쓰지 않음) */
	public enum Type {
		EXACT("01", "정확 일치"), ALIAS("02", "별칭"), AI_GUESS("03", "AI 후보(확인 필요)"), UNMAPPED("04", "미매핑");

		private final String code;
		private final String label;

		Type(String code, String label) {
			this.code = code;
			this.label = label;
		}

		public String code() {
			return code;
		}

		public String label() {
			return label;
		}
	}

	/**
	 * 매핑된 품목 한 줄. standardName · sizeCode 가 기준표 키 (미매핑이면 standardName 빈 문자열)
	 * standard: 찾은 규격 (없으면 null → 부피 0) · candidates: 복수 후보일 때 후보 품목 · reasons: 확인이 필요한 이유
	 */
	public record MappedItem(int index, AiItem ai, String standardName, String sizeCode, Type type, List<String> candidates,
			ItemStandard standard, boolean needsReview, List<String> reasons) {
	}

	private final ItemStandardCatalog catalog;

	public ItemMappingService(ItemStandardCatalog catalog) {
		this.catalog = catalog;
	}

	public List<MappedItem> map(List<AiItem> items) {
		return map(items, catalog.snapshot());
	}

	/** 기준표를 직접 넘기는 버전 (테스트용) */
	public static List<MappedItem> map(List<AiItem> items, ItemStandardCatalog.Snapshot table) {
		Map<String, String> names = new HashMap<>();
		table.names().forEach(n -> names.put(normalize(n), n));
		List<MappedItem> mapped = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		for (int i = 0; i < items.size(); i++) {
			AiItem ai = items.get(i);
			List<String> reasons = new ArrayList<>();
			String name = names.getOrDefault(normalize(ai.itemName()), "");
			String size = ai.sizeCode();
			Type type;
			List<String> candidates = List.of();
			if (!name.isEmpty()) {
				type = Type.EXACT;
				if (size.isEmpty()) {
					size = "02";
					type = Type.AI_GUESS;
					reasons.add("크기 코드가 없어 중형으로 계산했어요.");
				}
			} else {
				candidates = aliasCandidates(ai.itemName(), table.aliases(), names);
				if (candidates.isEmpty()) {
					candidates = aliasCandidates(ai.detectedName(), table.aliases(), names);
				}
				if (candidates.size() == 1) {
					type = Type.ALIAS;
					String[] target = candidates.get(0).split("\\|", -1);
					name = target[0];
					if (!target[1].isEmpty()) {
						size = target[1];
					} else if (size.isEmpty()) {
						size = "02";
						reasons.add("크기를 정하지 못해 중형으로 계산했어요.");
					}
					candidates = List.of();
				} else {
					type = Type.UNMAPPED;
					size = size.isEmpty() ? "02" : size;
					if (candidates.isEmpty()) {
						reasons.add("기준표에서 품목을 찾지 못했어요" + ("기타".equals(ai.itemName()) ? " (AI 가 기타로 답함)." : "."));
					} else {
						candidates = candidates.stream().map(c -> c.split("\\|", -1)[0]).distinct().toList();
						reasons.add("후보가 여러 개예요: " + String.join(", ", candidates));
					}
				}
			}
			ItemStandard standard = name.isEmpty() ? null : table.find(name, size);
			if (!name.isEmpty() && standard == null) {
				reasons.add("기준표에 " + name + " " + ItemStandard.sizeLabel(size) + " 규격이 없어요.");
			}
			if (ai.needsReview()) {
				reasons.add("AI 가 확인이 필요하다고 표시했어요.");
			}
			if (standard != null) {
				Integer first = seen.putIfAbsent(ai.room() + "|" + name + "|" + size, i + 1);
				if (first != null) {
					reasons.add(first + "번째 줄과 같은 공간 · 품목 · 크기예요 (중복 집계인지 확인).");
				}
			}
			mapped.add(new MappedItem(i + 1, ai, name, size, type, candidates, standard, !reasons.isEmpty(),
					List.copyOf(reasons)));
		}
		return List.copyOf(mapped);
	}

	/**
	 * 별칭 찾기: 공백을 무시하고 같은 별칭이 있으면 그것, 없으면 이름에 포함된 가장 긴 별칭들.
	 * 결과는 "품목명|크기코드" (기준표에 있는 품목만). 같은 길이로 서로 다른 품목이 걸리면 여러 개.
	 */
	static List<String> aliasCandidates(String text, List<Alias> aliases, Map<String, String> names) {
		String n = normalize(text);
		if (n.isEmpty()) {
			return List.of();
		}
		for (Alias a : aliases) {
			String target = names.get(normalize(a.itemName()));
			if (target != null && normalize(a.alias()).equals(n)) {
				return List.of(target + "|" + a.sizeCode());
			}
		}
		int best = 0;
		Set<String> found = new LinkedHashSet<>();
		for (Alias a : aliases) {
			String target = names.get(normalize(a.itemName()));
			String key = normalize(a.alias());
			if (target == null || key.isEmpty() || !n.contains(key)) {
				continue;
			}
			if (key.length() > best) {
				best = key.length();
				found.clear();
			}
			if (key.length() == best) {
				found.add(target + "|" + a.sizeCode());
			}
		}
		// 같은 품목인데 크기만 다른 후보는 크기 없이 하나로
		Set<String> items = new LinkedHashSet<>();
		found.forEach(f -> items.add(f.split("\\|", -1)[0]));
		if (items.size() == 1 && found.size() > 1) {
			return List.of(items.iterator().next() + "|");
		}
		return List.copyOf(found);
	}

	/** 비교용: 공백 · 가운뎃점 · 슬래시 · 괄호를 빼고 소문자로 */
	static String normalize(String s) {
		return s == null ? "" : s.toLowerCase().replaceAll("[\\s·ㆍ/()\\[\\]_-]", "");
	}
}
