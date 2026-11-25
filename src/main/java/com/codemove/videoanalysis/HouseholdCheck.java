package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.codemove.videoanalysis.AiResultParser.VideoState;
import com.codemove.videoanalysis.ItemMappingService.MappedItem;

/**
 * 생활 품목 점검: 보통 집에 함께 있는 품목끼리 앞뒤가 맞는지 보고, 빠졌거나 잘못 셌을 수 있는 품목을 표시한다.
 * 없는 물건을 자동으로 넣거나 수량을 바꾸지 않는다 (견적이 부풀려지지 않도록). 고객 확인 질문 · 관리자 표시에만 쓴다.
 * 특별한 물품(피아노 · 안마의자 · 어항 · 고가품)은 점검 대상이 아니다.
 *
 * 규칙 (house-01 에서 반복해 놓친 것 · 잘못 본 것에서 시작, 견적서가 쌓이면 고친다)
 * - H1 식탁 의자: 식탁 4인(02) 의자 4개 · 6인 이상(03) 6개보다 식탁 · 보조 의자가 적음
 * - H2 세탁기 · 건조기: 세탁기 2대 이상(하나는 건조기일 수 있음) · 세탁기는 있는데 건조기 없음
 * - H3 큰 가전 중복: 냉장고(일반 · 양문형) 2대 이상 · 세탁기 2대 이상은 H2
 * - H4 생활 필수품 없음: 냉장고 · 세탁기 · 주방용품
 * - H5 옷: 침대가 있는데 옷 · 행거 · 옷장이 모두 없음
 * - H6 짐 없는 공간: AI 가 본 공간인데 그 공간 품목이 하나도 없음 (드레스룸처럼 옷만 있는 곳은 짐이 있는 것으로 본다)
 * - H7 안 보인 공간: AI 가 안 보였다고 답한 공간 · 닫힌 수납장 (화장실 · 욕실 · 현관은 짐이 거의 없어 묻지 않음)
 * 신뢰도 점수의 가중치도 견적서와 맞춰 보기 전의 가정값이다.
 */
public final class HouseholdCheck {

	/** CHECK: 빠졌거나 잘못 셌을 가능성이 큼 · INFO: 고객에게 한 번 물어볼 만함 */
	public enum Level {
		CHECK, INFO
	}

	/** seen · expected: 수량 비교가 있는 규칙만 (없으면 null) · question: 고객 확인 화면에 띄울 질문 */
	public record Finding(String code, Level level, String item, String message, String question, Integer seen,
			Integer expected) {
	}

	/** score 0~100 · grade 높음(80↑) · 보통(60↑) · 낮음 · reasons: 점수를 깎은 이유 */
	public record Confidence(int score, String grade, List<String> reasons) {
	}

	public record Report(List<Finding> findings, Confidence confidence, String adminLabel) {
	}

	static final String CHAIR = "의자";
	static final String TABLE = "식탁·테이블";
	static final String WASHER = "세탁기";
	static final String DRYER = "건조기";
	static final String FRIDGE = "냉장고";
	static final String KITCHEN = "주방용품";
	static final String BED = "침대";
	static final String CLOTHES = "옷·행거";
	static final String WARDROBE = "옷장";
	/** 식탁 크기 → 보통 의자 수 (01 은 2인 · 좌식이라 의자가 없을 수 있어 비교하지 않음) */
	static final Map<String, Integer> CHAIRS_PER_TABLE = Map.of("02", 4, "03", 6);
	/** 회차별 수량 차이 표시 (RunEnsemble 이 evidence 에 남김) */
	static final String SPREAD_MARK = "회차별";
	/** 안 보인 공간 질문 최대 개수 */
	static final int MAX_UNSEEN = 4;

	private HouseholdCheck() {
	}

	public static Report check(List<MappedItem> items, List<String> rooms, VideoState state) {
		VideoState st = state == null ? VideoState.NONE : state;
		List<Finding> f = new ArrayList<>();
		Map<String, Integer> count = new LinkedHashMap<>();
		Map<String, Integer> bySize = new LinkedHashMap<>();
		for (MappedItem m : items) {
			if (m.standard() == null) {
				continue;
			}
			count.merge(m.standardName(), m.ai().quantity(), Integer::sum);
			bySize.merge(m.standardName() + "|" + m.sizeCode(), m.ai().quantity(), Integer::sum);
		}
		// H1 식탁 의자
		int expectedChairs = 0;
		for (Map.Entry<String, Integer> e : CHAIRS_PER_TABLE.entrySet()) {
			expectedChairs += e.getValue() * bySize.getOrDefault(TABLE + "|" + e.getKey(), 0);
		}
		int chairs = bySize.getOrDefault(CHAIR + "|01", 0);
		if (expectedChairs > 0 && chairs < expectedChairs) {
			f.add(new Finding("H1", Level.CHECK, CHAIR, "식탁 크기로 보면 의자가 " + expectedChairs + "개쯤인데 " + chairs + "개만 찾았어요.",
					"식탁 · 보조 의자가 " + chairs + "개로 보였어요. 실제로 몇 개인가요?", chairs, expectedChairs));
		}
		// H2 세탁기 · 건조기
		int washers = count.getOrDefault(WASHER, 0);
		int dryers = count.getOrDefault(DRYER, 0);
		boolean stacked = bySize.getOrDefault(WASHER + "|03", 0) > 0;
		if (washers >= 2 && dryers == 0) {
			f.add(new Finding("H2", Level.CHECK, WASHER, "세탁기를 " + washers + "대 찾았어요. 하나는 건조기일 수 있어요.",
					"세탁기가 " + washers + "대로 보였어요. 그중 건조기가 있나요?", washers, 1));
		} else if (washers >= 1 && dryers == 0 && !stacked) {
			f.add(new Finding("H2", Level.INFO, DRYER, "세탁기는 있는데 건조기는 찾지 못했어요.", "건조기도 가져가시나요?", 0, null));
		}
		// H3 큰 냉장고 중복
		int bigFridges = bySize.getOrDefault(FRIDGE + "|02", 0) + bySize.getOrDefault(FRIDGE + "|03", 0);
		if (bigFridges >= 2) {
			f.add(new Finding("H3", Level.CHECK, FRIDGE, "큰 냉장고를 " + bigFridges + "대 찾았어요. 같은 냉장고를 두 번 셌을 수 있어요.",
					"냉장고가 " + bigFridges + "대로 보였어요. 맞나요?", bigFridges, 1));
		}
		// H4 생활 필수품 (짐이 어느 정도 있는 집만: 품목이 거의 없으면 PARTIAL 판정이 따로 처리)
		boolean household = count.values().stream().mapToInt(Integer::intValue).sum() >= 5;
		if (household) {
			if (count.getOrDefault(FRIDGE, 0) == 0) {
				f.add(new Finding("H4", Level.CHECK, FRIDGE, "냉장고를 찾지 못했어요.", "영상에 냉장고가 보이지 않았어요. 가져가시나요?", 0, 1));
			}
			if (washers == 0 && !stacked) {
				f.add(new Finding("H4", Level.INFO, WASHER, "세탁기를 찾지 못했어요.", "영상에 세탁기가 보이지 않았어요. 가져가시나요?", 0, 1));
			}
			if (count.getOrDefault(KITCHEN, 0) == 0) {
				f.add(new Finding("H4", Level.INFO, KITCHEN, "주방용품(그릇 · 조리도구)을 찾지 못했어요.", "주방 그릇 · 조리도구는 몇 박스쯤 되나요?", 0, 1));
			}
		}
		// H5 옷
		if (count.getOrDefault(BED, 0) > 0 && count.getOrDefault(CLOTHES, 0) == 0 && count.getOrDefault(WARDROBE, 0) == 0) {
			f.add(new Finding("H5", Level.CHECK, CLOTHES, "침대는 있는데 옷 · 행거 · 옷장을 찾지 못했어요.", "옷은 어디에 얼마나 있나요? (행거 · 붙박이장 안 옷)",
					0, 1));
		}
		// H6 품목이 없는 공간
		Set<String> occupied = new LinkedHashSet<>();
		for (MappedItem m : items) {
			if (!m.ai().room().isBlank()) {
				occupied.add(room(m.ai().room()));
			}
		}
		for (String r : rooms == null ? List.<String>of() : rooms) {
			String key = room(r);
			if (!key.isEmpty() && !occupied.contains(key) && !r.matches(".*(복도|현관|베란다|발코니|다용도|팬트리|화장실|욕실).*")) {
				f.add(new Finding("H6", Level.INFO, r, r + "에서 찾은 짐이 없어요.", r + "에 옮길 짐이 있나요?", null, null));
			}
		}
		// H7 안 보인 공간
		// (여러 번 합친 결과는 회차마다 적은 곳이 겹치므로 고객 질문은 MAX_UNSEEN 개까지)
		int unseen = 0;
		for (String u : st.unseenAreas()) {
			if (u.matches(".*(화장실|욕실|현관).*")) {
				continue;
			}
			if (++unseen > MAX_UNSEEN) {
				break;
			}
			f.add(new Finding("H7", Level.INFO, u, "AI 가 잘 보지 못한 곳: " + u, u + "에 옮길 짐이 있나요?", null, null));
		}
		Confidence c = confidence(items, f, st);
		long checks = f.stream().filter(x -> x.level() == Level.CHECK).count();
		String label = "신뢰도 " + c.grade() + " " + c.score() + "점" + (f.isEmpty() ? "" : " · 확인 " + checks + " · 질문 " + (f.size() - checks));
		return new Report(List.copyOf(f), c, label);
	}

	/**
	 * 신뢰도 (가정값, 견적서로 맞출 것): 부피 기준 매핑 품질(정확 1.0 · 별칭 0.9 · AI 후보 0.6, 확인 필요 × 0.8) × 100
	 * - 회차별 수량 차이가 큰 품목의 부피 비율 × 20 - 미매핑 줄당 3(최대 15) - 점검 CHECK 5 · INFO 2 (합계 최대 30)
	 * - 범위 일부 15 · 거의없음 40 - 품질 문제 10
	 */
	static Confidence confidence(List<MappedItem> items, List<Finding> findings, VideoState st) {
		List<String> reasons = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		BigDecimal good = BigDecimal.ZERO;
		BigDecimal spread = BigDecimal.ZERO;
		int unmapped = 0;
		for (MappedItem m : items) {
			if (m.standard() == null) {
				unmapped++;
				continue;
			}
			BigDecimal cbm = m.standard().cbm().multiply(BigDecimal.valueOf(m.ai().quantity()));
			double w = switch (m.type()) {
				case EXACT -> 1.0;
				case ALIAS -> 0.9;
				default -> 0.6;
			};
			if (m.needsReview()) {
				w *= 0.8;
			}
			total = total.add(cbm);
			good = good.add(cbm.multiply(BigDecimal.valueOf(w)));
			if (m.ai().evidence() != null && m.ai().evidence().contains(SPREAD_MARK)) {
				spread = spread.add(cbm);
			}
		}
		double score = total.signum() == 0 ? 0 : good.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;
		if (score < 100 && total.signum() > 0) {
			reasons.add("매핑 · 확인 필요 품목 -" + Math.round(100 - score));
		}
		if (spread.signum() > 0) {
			double s = spread.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 20;
			score -= s;
			reasons.add("회차별 수량 차이 -" + Math.round(s));
		}
		if (unmapped > 0) {
			int u = Math.min(15, unmapped * 3);
			score -= u;
			reasons.add("기준표에 없는 품목 " + unmapped + "줄 -" + u);
		}
		int fp = Math.min(30, findings.stream().mapToInt(x -> x.level() == Level.CHECK ? 5 : 2).sum());
		if (fp > 0) {
			score -= fp;
			reasons.add("생활 품목 점검 " + findings.size() + "건 -" + fp);
		}
		if ("일부".equals(st.coverage())) {
			score -= 15;
			reasons.add("일부 공간만 보임 -15");
		} else if ("거의없음".equals(st.coverage())) {
			score -= 40;
			reasons.add("거의 안 보임 -40");
		}
		if (!st.qualityIssues().isEmpty()) {
			score -= 10;
			reasons.add("영상 품질(" + String.join(" · ", st.qualityIssues()) + ") -10");
		}
		int s = (int) Math.max(0, Math.min(100, Math.round(score)));
		return new Confidence(s, s >= 80 ? "높음" : s >= 60 ? "보통" : "낮음", List.copyOf(reasons));
	}

	/** 공간 이름 비교용 (공백 · 괄호 무시) */
	private static String room(String r) {
		return r == null ? "" : r.replaceAll("[\\s()]", "");
	}
}
