package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.codemove.videoanalysis.ItemMappingService.MappedItem;

import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 영상을 여러 번 분석한 결과를 품목별 중간값으로 합친다 (바구니 수처럼 실행마다 흔들리는 수량을 줄이기 위해).
 * - 품목 키: 기준표에 연결된 품목은 품목명 + 크기(매핑 결과 기준), 연결되지 않은 품목은 "기타" + 본 이름
 * - 회차마다 그 키의 수량 합(없으면 0)을 구해 Strategy 대로 고른다 (중간값이면 짝수 회는 아래쪽, 0 이면 뺀다 = 과반이 못 본 물건).
 * - 공간별 줄은 수량이 중간값과 같은(없으면 가장 가까운) 회차의 줄을 그대로 쓴다 → 방 정보가 남는다.
 * - 회차별 수량 차이가 크면(최대 - 최소 ≥ 2 이고 중간값의 30% 이상) 확인 필요 + evidence 에 "회차별 a~b개".
 * 결과는 AI 응답과 같은 형식의 JSON 글이라, 매핑 · 계산 · 점수는 기존 단계를 그대로 쓴다.
 */
public final class RunEnsemble {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private RunEnsemble() {
	}

	/**
	 * 합치는 방법. MEDIAN: 중간값(짝수 회면 아래쪽) · VOTE_MAX: 과반 회차가 본 품목만, 수량은 가장 많이 센 회차
	 * · MAX: 한 번이라도 본 품목, 수량은 가장 많이 센 회차 (house-01 에서 AI 는 덜 세는 쪽으로 틀려 정밀도가 96~98%라 최대값 쪽을 시험)
	 */
	public enum Strategy {
		MEDIAN, VOTE_MAX, MAX
	}

	public static String merge(List<AnalysisRun> runs) {
		return merge(runs, Strategy.MEDIAN);
	}

	/** 성공한 실행만 넘긴다 (2개 이상) */
	public static String merge(List<AnalysisRun> runs, Strategy strategy) {
		if (runs.size() < 2) {
			throw new IllegalArgumentException("합칠 실행이 2개 이상 필요해요.");
		}
		// 키 → 회차별 줄 목록
		Map<String, List<List<MappedItem>>> byKey = new LinkedHashMap<>();
		for (int r = 0; r < runs.size(); r++) {
			for (VolumeWeightService.Line line : runs.get(r).result().lines()) {
				MappedItem m = line.item();
				List<List<MappedItem>> perRun = byKey.computeIfAbsent(key(m), k -> {
					List<List<MappedItem>> l = new ArrayList<>();
					runs.forEach(x -> l.add(new ArrayList<>()));
					return l;
				});
				perRun.get(r).add(m);
			}
		}
		List<Map<String, Object>> items = new ArrayList<>();
		for (List<List<MappedItem>> perRun : byKey.values()) {
			int[] q = perRun.stream().mapToInt(l -> l.stream().mapToInt(m -> m.ai().quantity()).sum()).toArray();
			int[] sorted = q.clone();
			java.util.Arrays.sort(sorted);
			int seen = (int) java.util.Arrays.stream(q).filter(x -> x > 0).count();
			int target = switch (strategy) {
				case MEDIAN -> sorted[(sorted.length - 1) / 2];
				case VOTE_MAX -> seen * 2 > q.length ? sorted[sorted.length - 1] : 0;
				case MAX -> sorted[sorted.length - 1];
			};
			if (target <= 0) {
				continue;
			}
			int min = sorted[0];
			int max = sorted[sorted.length - 1];
			boolean spread = max - min >= 2 && max - min >= target * 0.3;
			// 고른 수량에 가장 가까운 회차의 줄 (같으면 앞 회차)
			int pick = 0;
			for (int r = 1; r < q.length; r++) {
				if (Math.abs(q[r] - target) < Math.abs(q[pick] - target)) {
					pick = r;
				}
			}
			List<MappedItem> lines = new ArrayList<>(perRun.get(pick));
			lines.sort(Comparator.comparingInt(m -> -m.ai().quantity()));
			// 그 회차의 합이 고른 수량과 다르면 가장 큰 줄에서 맞춘다
			int diff = target - q[pick];
			for (int i = 0; i < lines.size(); i++) {
				MappedItem m = lines.get(i);
				int quantity = m.ai().quantity() + (i == 0 ? diff : 0);
				if (quantity <= 0) {
					continue;
				}
				items.add(item(m, quantity, spread ? "회차별 " + min + "~" + max + "개" : null));
			}
		}
		AnalysisRun first = runs.get(0);
		Set<String> rooms = new LinkedHashSet<>();
		Set<String> unseen = new LinkedHashSet<>();
		Set<String> quality = new LinkedHashSet<>();
		Map<String, Integer> coverage = new LinkedHashMap<>();
		List<String> memo = new ArrayList<>();
		for (AnalysisRun r : runs) {
			if (r.ai() == null) {
				continue;
			}
			rooms.addAll(r.ai().rooms());
			if (r.ai().state() != null) {
				unseen.addAll(r.ai().state().unseenAreas());
				quality.addAll(r.ai().state().qualityIssues());
				if (!r.ai().state().coverage().isEmpty()) {
					coverage.merge(r.ai().state().coverage(), 1, Integer::sum);
				}
			}
			if (r.ai().memo() != null && !r.ai().memo().isBlank() && memo.isEmpty()) {
				memo.add(r.ai().memo());
			}
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("video_ok", true);
		root.put("rooms", List.copyOf(rooms));
		root.put("items", items);
		root.put("memo", memo.isEmpty() ? "" : memo.get(0));
		root.put("quality_issues", List.copyOf(quality));
		coverage.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> root.put("coverage", e.getKey()));
		root.put("unseen_areas", List.copyOf(unseen));
		root.put("ensemble_of", runs.stream().map(AnalysisRun::id).toList());
		root.put("ensemble_base", first.id());
		root.put("ensemble_strategy", strategy.name());
		return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root);
	}

	static String key(MappedItem m) {
		if (m.standard() != null) {
			return m.standardName() + "|" + m.sizeCode();
		}
		String name = m.ai().detectedName().isEmpty() ? m.ai().itemName() : m.ai().detectedName();
		return "기타|" + ItemMappingService.normalize(name);
	}

	private static Map<String, Object> item(MappedItem m, int quantity, String spreadNote) {
		// AI 가 답한 이름 · 크기를 그대로 둬서 다시 매핑해도 같은 품목 · 같은 매핑 유형이 나오게 한다
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("item_name", m.ai().itemName());
		o.put("size_code", m.ai().sizeCode());
		o.put("quantity", quantity);
		o.put("detected_name", m.ai().detectedName());
		o.put("room", m.ai().room());
		String evidence = m.ai().evidence() == null ? "" : m.ai().evidence();
		o.put("evidence", spreadNote == null ? evidence : (evidence.isEmpty() ? spreadNote : evidence + " · " + spreadNote));
		o.put("needs_review", m.ai().needsReview() || spreadNote != null);
		o.put("inch", m.ai().inch());
		o.put("wall_mounted", m.ai().wallMounted());
		return o;
	}
}
