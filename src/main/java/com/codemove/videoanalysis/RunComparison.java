package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

/**
 * 조건별 비교표 (가이드 7단계: 정답 대비 정확도 · 시간 · 비용을 표로).
 * 같은 영상(영상 파일 이름 = 정답 목록 이름)의 영상 분석 기록을 실제로 쓰인 모델 · 프롬프트 · 초당 프레임으로 묶는다.
 * 실패도 시도에 넣어 성공률 · 실패 이유를 보이고, 정확도 · 비용은 성공한 기록으로만 낸다.
 */
@Service
public class RunComparison {

	private final GroundTruthScorer scorer;
	private final TruckLoadService trucks;
	private final VideoAnalysisProperties properties;

	/** 평균 · 최소 · 최대 (값이 없으면 모두 null) */
	public record Stat(BigDecimal avg, BigDecimal min, BigDecimal max) {
	}

	/**
	 * tonMatch: 정답 기준 톤과 같은 톤이 나온 횟수 · errors: 실패 코드별 횟수 · cost: 1회 비용(USD, 단가가 있는 기록만)
	 */
	public record Row(String model, String promptVersion, String fps, int attempts, int successes, Map<String, Integer> errors,
			Stat recall, Stat precision, Stat f1, Stat cbmErrorPct, Stat ton, int tonMatch, Stat apiSec, Stat inputTokens,
			Stat totalTokens, Stat cost) {
	}

	/** expectedTon: 정답 목록 부피로 낸 톤 (대표님 기준과 비교하는 목표) */
	public record Table(String set, BigDecimal expectedTon, String expectedLabel, List<Row> rows) {
	}

	public RunComparison(GroundTruthScorer scorer, TruckLoadService trucks, VideoAnalysisProperties properties) {
		this.scorer = scorer;
		this.trucks = trucks;
		this.properties = properties;
	}

	/** 없는 테스트셋이면 null */
	public Table compare(String set, List<AnalysisRun> runs, ItemStandardCatalog.Snapshot table) {
		if (set == null || !scorer.sets().contains(set)) {
			return null;
		}
		BigDecimal expectedTon = null;
		String expectedLabel = "";
		Map<String, List<AnalysisRun>> groups = new TreeMap<>();
		for (AnalysisRun r : runs) {
			boolean ensemble = VideoAnalysisService.ENSEMBLE.equals(r.mode());
			if (!("GEMINI".equals(r.mode()) || ensemble) || r.video() == null || !set.equals(stem(r.video().fileName()))) {
				continue;
			}
			String fps = r.fps() == null ? "기본" : BigDecimal.valueOf(r.fps()).stripTrailingZeros().toPlainString();
			// 여러 번 분석 중간값은 따로 묶는다 (비용 · 토큰은 합친 회차 전체)
			String prompt = nz(r.promptVersion()) + (ensemble ? " · " + ensembleLabel(r) : "");
			groups.computeIfAbsent(nz(r.model()) + "|" + prompt + "|" + fps, k -> new ArrayList<>()).add(r);
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, List<AnalysisRun>> g : groups.entrySet()) {
			String[] key = g.getKey().split("\\|", -1);
			Map<String, Integer> errors = new LinkedHashMap<>();
			List<BigDecimal> recall = new ArrayList<>(), precision = new ArrayList<>(), f1 = new ArrayList<>(), err = new ArrayList<>(),
					ton = new ArrayList<>(), api = new ArrayList<>(), input = new ArrayList<>(), total = new ArrayList<>(),
					cost = new ArrayList<>();
			int successes = 0;
			int tonMatch = 0;
			for (AnalysisRun r : g.getValue()) {
				if (!r.success() || r.result() == null) {
					errors.merge(r.errorCode() == null ? "?" : r.errorCode(), 1, Integer::sum);
					continue;
				}
				successes++;
				GroundTruthScorer.Score s = scorer.score(set, r.result().lines().stream().map(VolumeWeightService.Line::item).toList(),
						table);
				if (s != null) {
					recall.add(s.recall());
					precision.add(s.precision());
					f1.add(s.f1());
					err.add(s.cbmErrorPct());
					if (expectedTon == null) {
						TruckLoadService.Load exp = trucks.estimate(s.expectedCbm(), table.trucks());
						if (exp != null) {
							expectedTon = exp.ton();
							expectedLabel = exp.label();
						}
					}
				}
				if (r.load() != null) {
					ton.add(r.load().ton());
					if (expectedTon != null && r.load().ton().compareTo(expectedTon) == 0) {
						tonMatch++;
					}
				}
				if (r.timings() != null && r.timings().apiMs() != null) {
					api.add(BigDecimal.valueOf(r.timings().apiMs()).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP));
				}
				if (r.usage() != null) {
					input.add(BigDecimal.valueOf(r.usage().promptTokens()));
					total.add(BigDecimal.valueOf(r.usage().totalTokens()));
				}
				// 비용은 저장된 값이 아니라 지금 단가표로 다시 계산 (단가를 바꾼 뒤에도 같은 기준으로 비교)
				VideoAnalysisProperties.ModelPrice price = properties.price().find(r.model(), r.modelVersion());
				if (r.usage() != null && price != null) {
					cost.add(BigDecimal.valueOf(r.usage().promptTokens()).multiply(price.inputPerMillion())
							.add(BigDecimal.valueOf((long) r.usage().outputTokens() + r.usage().thoughtsTokens()).multiply(price.outputPerMillion()))
							.divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP));
				}
			}
			rows.add(new Row(key[0], key[1], key[2], g.getValue().size(), successes, errors, stat(recall, 3), stat(precision, 3),
					stat(f1, 3), stat(err, 1), stat(ton, 1), tonMatch, stat(api, 1), stat(input, 0), stat(total, 0), stat(cost, 6)));
		}
		return new Table(set, expectedTon, expectedLabel, List.copyOf(rows));
	}

	static Stat stat(List<BigDecimal> values, int scale) {
		if (values.isEmpty()) {
			return new Stat(null, null, null);
		}
		BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Stat(sum.divide(BigDecimal.valueOf(values.size()), scale, RoundingMode.HALF_UP),
				values.stream().min(BigDecimal::compareTo).orElseThrow().setScale(scale, RoundingMode.HALF_UP),
				values.stream().max(BigDecimal::compareTo).orElseThrow().setScale(scale, RoundingMode.HALF_UP));
	}

	/** 합친 회차 수 + 방법 (예: "3회 중간값"). 합친 결과 JSON 의 ensemble_of · ensemble_strategy 에서 읽는다 */
	static String ensembleLabel(AnalysisRun r) {
		String raw = r.rawResponse() == null ? "" : r.rawResponse();
		java.util.regex.Matcher s = java.util.regex.Pattern.compile("\"ensemble_strategy\"\\s*:\\s*\"(\\w+)\"").matcher(raw);
		RunEnsemble.Strategy how = RunEnsemble.Strategy.MEDIAN;
		if (s.find()) {
			try {
				how = RunEnsemble.Strategy.valueOf(s.group(1));
			} catch (IllegalArgumentException e) {
				// 모르는 값이면 중간값
			}
		}
		java.util.regex.Matcher of = java.util.regex.Pattern.compile("\"ensemble_of\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(raw);
		int n = of.find() ? of.group(1).split(",").length : 0;
		return (n > 0 ? n + "회 " : "") + VideoAnalysisService.STRATEGY_NAME.get(how);
	}

	/** 파일 이름에서 확장자를 뺀 것 */
	static String stem(String fileName) {
		if (fileName == null) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? fileName : fileName.substring(0, dot);
	}

	private static String nz(String s) {
		return s == null || s.isBlank() ? "-" : s;
	}
}
