package com.codemove.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 이삿짐 표준 부피 (중형 1개 기준 CBM) · 카테고리 · 아이콘.
 * 품목 선택 시트 목록과 quote.js 의 부피 계산이 이 표를 함께 쓴다 (화면에는 data 로 내려보냄).
 * 표에 없는 이름(AI 가 낸 낯선 품목)은 기타 · 0.5 CBM 으로 본다.
 */
@Component
public class ItemCatalog {

	/** ITEM_SIZE 코드별 크기 계수: 01 소형 · 02 중형 · 03 대형 */
	private static final Map<String, BigDecimal> SIZE_FACTOR = Map.of(
			"01", new BigDecimal("0.75"), "02", BigDecimal.ONE, "03", new BigDecimal("1.25"));

	private static final Item UNKNOWN = new Item("", "04", "item-box", 0.5, false);

	/** pickable=false: AI 결과에만 나오고 선택 시트에는 없는 이름 */
	public record Item(String name, String category, String icon, double baseCbm, boolean pickable) {
	}

	private final Map<String, Item> items = new LinkedHashMap<>();

	public ItemCatalog() {
		// 01 가구
		add("침대", "01", "item-bed", 2.4);
		add("소파", "01", "item-sofa", 2.1);
		add("옷장", "01", "item-wardrobe", 2.0);
		add("서랍장", "01", "item-wardrobe", 0.9);
		add("책상", "01", "item-table", 0.9);
		add("식탁·테이블", "01", "item-table", 0.7 / 0.75);
		add("의자", "01", "item-box", 0.5);
		add("책장", "01", "item-wardrobe", 1.0);
		add("화장대", "01", "item-table", 0.6);
		add("TV장", "01", "item-table", 0.6);
		add("수납장", "01", "item-wardrobe", 0.8);
		items.put("테이블", new Item("테이블", "01", "item-table", 0.7 / 0.75, false));
		// 02 가전
		add("냉장고", "02", "item-fridge", 1.8);
		add("세탁기", "02", "item-washer", 1.2);
		add("TV", "02", "item-tv", 0.4);
		add("에어컨", "02", "item-box", 0.5);
		add("건조기", "02", "item-washer", 1.0);
		add("김치냉장고", "02", "item-fridge", 0.9);
		add("전자레인지", "02", "item-box", 0.5);
		add("정수기", "02", "item-box", 0.5);
		add("컴퓨터", "02", "item-box", 0.5);
		// 03 생활용품
		add("이사박스", "03", "item-box", 4.6 / 13);
		add("이불·침구", "03", "item-box", 0.5);
		add("옷·행거", "03", "item-box", 0.5);
		add("주방용품", "03", "item-box", 0.5);
		add("자전거", "03", "item-box", 0.5);
		add("캐리어", "03", "item-box", 0.5);
		// 04 기타
		add("피아노", "04", "item-box", 0.5);
		add("화분", "04", "item-box", 0.5);
		add("운동기구", "04", "item-box", 0.5);
		add("안마의자", "04", "item-box", 0.5);
		add("어항", "04", "item-box", 0.5);
	}

	private void add(String name, String category, String icon, double baseCbm) {
		items.put(name, new Item(name, category, icon, baseCbm, true));
	}

	public Item find(String name) {
		Item item = items.get(name);
		return item != null ? item : new Item(name, UNKNOWN.category(), UNKNOWN.icon(), UNKNOWN.baseCbm(), false);
	}

	public boolean isValidSize(String sizeCode) {
		return SIZE_FACTOR.containsKey(sizeCode);
	}

	/** 수량을 반영한 총 부피 (소수 둘째 자리 반올림, VOLUME NUMBER(5,2)) */
	public BigDecimal volume(String name, String sizeCode, int quantity) {
		return BigDecimal.valueOf(find(name).baseCbm()).multiply(SIZE_FACTOR.get(sizeCode))
				.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
	}

	/** 선택 시트 목록: 카테고리 코드 → 품목 이름 */
	public Map<String, List<String>> pickable() {
		Map<String, List<String>> byCategory = new LinkedHashMap<>();
		items.values().stream().filter(Item::pickable)
				.forEach(i -> byCategory.computeIfAbsent(i.category(), k -> new java.util.ArrayList<>()).add(i.name()));
		return byCategory;
	}

	/** quote.js 용: 이름 → [아이콘, 기준 CBM, 카테고리] */
	public Map<String, List<Object>> forScript() {
		Map<String, List<Object>> map = new LinkedHashMap<>();
		items.values().forEach(i -> map.put(i.name(), List.of(i.icon(), i.baseCbm(), i.category())));
		return map;
	}
}
