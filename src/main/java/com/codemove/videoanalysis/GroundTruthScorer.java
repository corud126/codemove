package com.codemove.videoanalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codemove.videoanalysis.ItemMappingService.MappedItem;
import com.codemove.videoanalysis.ItemMappingService.Type;

/**
 * 정답 목록(사람이 영상을 보고 기록한 CSV: 공간,품목명,크기코드,수량,…)과 AI 결과 비교 (가이드 9장, 팀 기준 GroundTruth 와 같은 방식).
 * - 품목 인식: 품목명별 수량 겹침(min)으로 Precision · Recall · F1
 * - 부피: 정답 규격 기준 CBM 과의 오차 (실측 정확도가 아니라 표준 규격 기준)
 * - 품목표에 없는 물건(정답 "기타", AI 미매핑)은 이름이 제각각이라 개수만 따로 센다.
 * 정답 목록 폴더: codemove.video-analysis.testset-dir (파일 이름 = 테스트셋 이름)
 */
@Service
public class GroundTruthScorer {

	private final Path dir;

	/** 품목명별 비교 한 줄 */
	public record Row(String name, int expected, int detected, int matched, BigDecimal expectedCbm, BigDecimal detectedCbm) {
	}

	public record Score(String set, List<Row> rows, int expectedPieces, int detectedPieces, int matchedPieces,
			BigDecimal precision, BigDecimal recall, BigDecimal f1, BigDecimal expectedCbm, BigDecimal detectedCbm,
			BigDecimal cbmErrorPct, int expectedOther, int detectedOther) {
	}

	/** 정답 한 줄 */
	public record Line(String name, String sizeCode, int quantity) {
	}

	public GroundTruthScorer(@Value("${codemove.video-analysis.testset-dir:}") String testsetDir) {
		this.dir = testsetDir == null || testsetDir.isBlank() ? null : Path.of(testsetDir.trim());
	}

	/** 테스트셋 이름 목록 (폴더의 .csv 파일 이름) */
	public List<String> sets() {
		if (dir == null || !Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".csv"))
					.map(n -> n.substring(0, n.length() - 4)).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** 없는 테스트셋이면 null */
	public Score score(String set, List<MappedItem> items, ItemStandardCatalog.Snapshot table) {
		if (set == null || !sets().contains(set)) {
			return null;
		}
		try {
			return score(set, lines(Files.readString(dir.resolve(set + ".csv"), StandardCharsets.UTF_8)), items, table);
		} catch (IOException e) {
			return null;
		}
	}

	/** 공간,품목명,크기코드,수량,… (수량이 숫자가 아닌 줄은 건너뜀) */
	static List<Line> lines(String csv) {
		List<List<String>> rows = CsvReader.read(csv);
		List<Line> out = new ArrayList<>();
		for (List<String> r : rows.subList(Math.min(1, rows.size()), rows.size())) {
			if (r.size() < 4) {
				continue;
			}
			try {
				out.add(new Line(r.get(1).trim(), r.get(2).trim(), Integer.parseInt(r.get(3).trim())));
			} catch (NumberFormatException e) {
				// 머리글 · 빈 줄
			}
		}
		return out;
	}

	static Score score(String set, List<Line> truth, List<MappedItem> items, ItemStandardCatalog.Snapshot table) {
		Map<String, int[]> qty = new LinkedHashMap<>(); // 품목명 → {정답, AI}
		Map<String, BigDecimal[]> cbm = new LinkedHashMap<>();
		int expOther = 0;
		int detOther = 0;
		for (Line l : truth) {
			ItemStandard s = table.find(l.name(), ItemStandard.sizeCodeOf(l.sizeCode()) == null ? "02" : ItemStandard.sizeCodeOf(l.sizeCode()));
			if (s == null) {
				expOther += l.quantity();
				continue;
			}
			qty.computeIfAbsent(l.name(), k -> new int[2])[0] += l.quantity();
			cbm.computeIfAbsent(l.name(), k -> zero())[0] = cbm.get(l.name())[0].add(s.cbm().multiply(BigDecimal.valueOf(l.quantity())));
		}
		for (MappedItem m : items) {
			if (m.type() == Type.UNMAPPED || m.standard() == null) {
				detOther += m.ai().quantity();
				continue;
			}
			String name = m.standardName();
			qty.computeIfAbsent(name, k -> new int[2])[1] += m.ai().quantity();
			cbm.computeIfAbsent(name, k -> zero())[1] = cbm.get(name)[1].add(m.standard().cbm().multiply(BigDecimal.valueOf(m.ai().quantity())));
		}
		List<Row> rows = new ArrayList<>();
		int e = 0;
		int d = 0;
		int matched = 0;
		BigDecimal ec = BigDecimal.ZERO;
		BigDecimal dc = BigDecimal.ZERO;
		for (Map.Entry<String, int[]> en : qty.entrySet()) {
			BigDecimal[] c = cbm.get(en.getKey());
			int m = Math.min(en.getValue()[0], en.getValue()[1]);
			rows.add(new Row(en.getKey(), en.getValue()[0], en.getValue()[1], m, c[0].setScale(3, RoundingMode.HALF_UP),
					c[1].setScale(3, RoundingMode.HALF_UP)));
			e += en.getValue()[0];
			d += en.getValue()[1];
			matched += m;
			ec = ec.add(c[0]);
			dc = dc.add(c[1]);
		}
		BigDecimal p = ratio(matched, d);
		BigDecimal r = ratio(matched, e);
		BigDecimal f1 = p.add(r).signum() == 0 ? BigDecimal.ZERO.setScale(3)
				: p.multiply(r).multiply(BigDecimal.valueOf(2)).divide(p.add(r), 3, RoundingMode.HALF_UP);
		BigDecimal err = ec.signum() == 0 ? BigDecimal.ZERO.setScale(1)
				: dc.subtract(ec).multiply(BigDecimal.valueOf(100)).divide(ec, 1, RoundingMode.HALF_UP);
		return new Score(set, List.copyOf(rows), e, d, matched, p, r, f1, ec.setScale(3, RoundingMode.HALF_UP),
				dc.setScale(3, RoundingMode.HALF_UP), err, expOther, detOther);
	}

	private static BigDecimal[] zero() {
		return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
	}

	private static BigDecimal ratio(int a, int b) {
		return b == 0 ? BigDecimal.ZERO.setScale(3) : BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 3, RoundingMode.HALF_UP);
	}
}
