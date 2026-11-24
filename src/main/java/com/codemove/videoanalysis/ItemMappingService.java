package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.codemove.videoanalysis.AiResultParser.AiItem;

/**
 * AI 인식 결과 → 표준 품목 · 규격 매핑.
 * 순서: AI 가 고른 기준표 품목명 일치(STANDARD) → 인식명 일치(EXACT) → 별칭 포함(ALIAS) → 실패(UNMAPPED).
 * 별칭 후보가 같은 길이로 여러 품목에 걸리면 무리하게 정하지 않고 AMBIGUOUS(확인 필요)로 남긴다.
 */
@Service
public class ItemMappingService {

	public enum Method {
		STANDARD("AI 기준표 선택"), EXACT("이름 일치"), ALIAS("별칭"), AMBIGUOUS("복수 후보"), UNMAPPED("매핑 실패");

		private final String label;

		Method(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/**
	 * 매핑된 품목 한 줄. standardName · sizeCode 가 기준표 키 (UNMAPPED 면 standardName 빈 문자열)
	 * standard: 찾은 규격 (기준표에 그 크기가 없거나 매핑 실패면 null) · reasons: 확인이 필요한 이유
	 */
	public record MappedItem(int index, AiItem ai, String standardName, String sizeCode, Method method,
			List<String> candidates, ItemStandard standard, boolean needsReview, List<String> reasons) {
	}

	/** 별칭(공백 · 기호를 뺀 소문자) → 기준표 품목명. 기준표에 없는 품목명으로 가는 별칭은 무시된다 */
	static final Map<String, String> ALIASES = aliases(
			"침대", "침대", "매트리스", "침대", "돌침대", "침대", "이층침대", "침대",
			"소파", "소파", "쇼파", "소파",
			"옷장", "옷장", "장롱", "옷장", "붙박이장", "옷장", "드레스장", "옷장",
			"서랍장", "서랍장", "협탁", "서랍장",
			"책상", "책상", "컴퓨터책상", "책상",
			"식탁", "식탁·테이블", "테이블", "식탁·테이블", "좌식테이블", "식탁·테이블", "탁자", "식탁·테이블",
			"의자", "의자", "스툴", "의자",
			"책장", "책장", "선반", "책장", "선반장", "책장",
			"화장대", "화장대",
			"tv장", "TV장", "거실장", "TV장",
			"수납장", "수납장", "신발장", "수납장", "장식장", "수납장", "주방수납장", "수납장",
			"냉장고", "냉장고", "양문형냉장고", "냉장고",
			"김치냉장고", "김치냉장고", "김냉", "김치냉장고",
			"세탁기", "세탁기", "드럼세탁기", "세탁기", "통돌이", "세탁기", "워시타워", "세탁기",
			"tv", "TV", "티비", "TV", "텔레비전", "TV",
			"에어컨", "에어컨", "실외기", "에어컨",
			"건조기", "건조기", "의류건조기", "건조기",
			"전자레인지", "전자레인지", "오븐", "전자레인지", "광파오븐", "전자레인지",
			"정수기", "정수기",
			"컴퓨터", "컴퓨터", "pc", "컴퓨터", "데스크톱", "컴퓨터", "모니터", "컴퓨터",
			"이사박스", "이사박스", "박스", "이사박스", "상자", "이사박스", "바구니", "이사박스",
			"이불", "이불·침구", "침구", "이불·침구", "베개", "이불·침구",
			"행거", "옷·행거", "옷", "옷·행거",
			"주방용품", "주방용품", "그릇", "주방용품", "냄비", "주방용품",
			"자전거", "자전거",
			"캐리어", "캐리어", "여행가방", "캐리어",
			"피아노", "피아노", "디지털피아노", "피아노",
			"화분", "화분", "식물", "화분",
			"운동기구", "운동기구", "러닝머신", "운동기구", "실내자전거", "운동기구",
			"안마의자", "안마의자",
			"어항", "어항", "수조", "어항");

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
			Method method;
			String name = "";
			List<String> candidates = List.of();
			if (names.containsKey(normalize(ai.standardName()))) {
				method = Method.STANDARD;
				name = names.get(normalize(ai.standardName()));
			} else if (names.containsKey(normalize(ai.detectedName()))) {
				method = Method.EXACT;
				name = names.get(normalize(ai.detectedName()));
			} else {
				candidates = aliasCandidates(ai.detectedName(), names);
				if (candidates.size() == 1) {
					method = Method.ALIAS;
					name = candidates.get(0);
				} else if (candidates.isEmpty()) {
					method = Method.UNMAPPED;
					reasons.add("기준표에서 품목을 찾지 못했어요.");
				} else {
					method = Method.AMBIGUOUS;
					reasons.add("후보가 여러 개예요: " + String.join(", ", candidates));
				}
			}
			String sizeCode = ItemStandard.sizeCodeOf(ai.sizeGrade());
			if (sizeCode == null) {
				sizeCode = "02";
				reasons.add("크기 등급이 없어 중형으로 계산했어요.");
			}
			ItemStandard standard = name.isEmpty() ? null : table.byKey().get(name + "|" + sizeCode);
			if (!name.isEmpty() && standard == null) {
				reasons.add("기준표에 " + name + " " + ItemStandard.sizeLabel(sizeCode) + " 규격이 없어요.");
			}
			if (ai.needsReview()) {
				reasons.add("AI 가 확인이 필요하다고 표시했어요.");
			}
			if (standard != null) {
				String key = name + "|" + sizeCode;
				Integer first = seen.putIfAbsent(key, i + 1);
				if (first != null) {
					reasons.add(first + "번째 줄과 같은 품목 · 크기예요 (중복 집계인지 확인).");
				}
			}
			mapped.add(new MappedItem(i + 1, ai, name, sizeCode, method, candidates, standard, !reasons.isEmpty(),
					List.copyOf(reasons)));
		}
		return List.copyOf(mapped);
	}

	/** 인식명에 포함된 별칭 중 가장 긴 것들이 가리키는 품목명 (기준표에 있는 것만) */
	static List<String> aliasCandidates(String detected, Map<String, String> names) {
		String text = normalize(detected);
		int best = 0;
		Set<String> found = new LinkedHashSet<>();
		for (Map.Entry<String, String> e : ALIASES.entrySet()) {
			String target = names.get(normalize(e.getValue()));
			if (target == null || !text.contains(e.getKey())) {
				continue;
			}
			int len = e.getKey().length();
			if (len > best) {
				best = len;
				found.clear();
			}
			if (len == best) {
				found.add(target);
			}
		}
		return List.copyOf(found);
	}

	/** 비교용: 공백 · 가운뎃점 · 슬래시 · 괄호를 빼고 소문자로 */
	static String normalize(String s) {
		return s == null ? "" : s.toLowerCase().replaceAll("[\\s·ㆍ/()\\[\\]_-]", "");
	}

	private static Map<String, String> aliases(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(normalize(pairs[i]), pairs[i + 1]);
		}
		return Map.copyOf(map);
	}
}
