package com.codemove.videoanalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 영상 분석 기준 데이터 (검증 단계라 DB 가 아니라 파일에서 읽는다. 고치고 [다시 읽기]만 하면 바로 다시 테스트).
 * - 품목 기준표 (codemove.video-analysis.standard-csv): 품목명 · 카테고리 · 크기코드 · 크기명 · 판정기준 · 제안CBM · 무게kg · 옵션코드 · 근거
 * - 별칭 (alias-csv): 별칭 · 품목명 · 크기코드(없으면 AI 가 고른 크기) · 비고 — 팀 기준(item-alias.csv) + 우리 추가
 * - 트럭 적재 (truck-csv): 톤 · 적재CBM — 대표님 1톤 10 · 5톤 35 CBM (2.5 · 3.5톤은 가정)
 * 열은 헤더 이름으로 찾는다. 나중에 V4 테이블(TBL_ITEM_SPEC · TBL_ITEM_ALIAS · TBL_TRUCK)로 옮긴다.
 */
@Slf4j
@Component
public class ItemStandardCatalog {

	/** 카테고리 코드(ITEM_CATEGORY) → 이름 */
	public static final Map<String, String> CATEGORIES = Map.of("01", "가구", "02", "가전", "03", "생활용품", "04", "기타");

	/** sizeCode 가 빈 문자열이면 AI 가 고른 크기를 쓴다 */
	public record Alias(String alias, String itemName, String sizeCode, String note) {
	}

	/** ton: 짐 톤수 · loadCbm: 그 톤에 싣는 적재 부피 */
	public record Truck(BigDecimal ton, BigDecimal loadCbm) {
	}

	/** list: 파일 순서 · error: 읽기 실패 사유(없으면 null, 별칭 · 트럭 파일 문제도 여기에) */
	public record Snapshot(List<ItemStandard> list, Map<String, ItemStandard> byKey, List<Alias> aliases,
			List<Truck> trucks, String source, LocalDateTime loadedAt, String error) {

		public boolean ready() {
			return error == null && !list.isEmpty();
		}

		/** 품목 이름 목록 (중복 없이, 파일 순서) */
		public List<String> names() {
			return list.stream().map(ItemStandard::name).distinct().toList();
		}

		public ItemStandard find(String name, String sizeCode) {
			return byKey.get(name + "|" + sizeCode);
		}
	}

	private final Path csv;
	private final Path aliasCsv;
	private final Path truckCsv;
	private volatile Snapshot snapshot;

	public ItemStandardCatalog(@Value("${codemove.video-analysis.standard-csv:}") String csvPath,
			@Value("${codemove.video-analysis.alias-csv:}") String aliasPath,
			@Value("${codemove.video-analysis.truck-csv:}") String truckPath) {
		this.csv = path(csvPath);
		this.aliasCsv = path(aliasPath);
		this.truckCsv = path(truckPath);
		reload();
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	/** 파일을 다시 읽는다. 실패해도 예외 대신 error 가 채워진 기준표 */
	public synchronized Snapshot reload() {
		List<String> problems = new ArrayList<>();
		Snapshot table = null;
		if (csv == null) {
			problems.add("기준표 경로(codemove.video-analysis.standard-csv)가 설정되지 않았어요.");
		} else {
			try {
				table = parse(read(csv), csv.toString());
			} catch (IOException | IllegalArgumentException e) {
				log.warn("품목 기준표 읽기 실패: {}", csv, e);
				problems.add("기준표를 읽지 못했어요: " + e.getMessage());
			}
		}
		List<Alias> aliases = List.of();
		if (aliasCsv != null) {
			try {
				aliases = parseAliases(read(aliasCsv));
			} catch (IOException | IllegalArgumentException e) {
				problems.add("별칭 파일을 읽지 못했어요: " + e.getMessage());
			}
		}
		List<Truck> trucks = List.of();
		if (truckCsv != null) {
			try {
				trucks = parseTrucks(read(truckCsv));
			} catch (IOException | IllegalArgumentException e) {
				problems.add("트럭 적재 파일을 읽지 못했어요: " + e.getMessage());
			}
		}
		snapshot = new Snapshot(table == null ? List.of() : table.list(), table == null ? Map.of() : table.byKey(), aliases,
				trucks, csv == null ? "" : csv.toString(), LocalDateTime.now(), problems.isEmpty() ? null : String.join(" ", problems));
		return snapshot;
	}

	public ItemStandard find(String name, String sizeCode) {
		return snapshot.find(name, sizeCode);
	}

	private static Path path(String p) {
		return p == null || p.isBlank() ? null : Path.of(p.trim());
	}

	private static String read(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			throw new IOException("파일이 없어요: " + file);
		}
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	/** 품목 기준표 CSV 본문 → 기준표 (별칭 · 트럭은 빈 목록, 테스트에서도 쓴다). 필수 열이 없으면 IllegalArgumentException */
	static Snapshot parse(String text, String source) {
		Table t = table(text, "품목명", "카테고리", "크기코드", "제안CBM");
		List<ItemStandard> list = new ArrayList<>();
		Map<String, ItemStandard> byKey = new LinkedHashMap<>();
		for (List<String> row : t.rows()) {
			String name = t.cell(row, "품목명");
			String size = ItemStandard.sizeCodeOf(t.cell(row, "크기코드"));
			BigDecimal cbm = decimal(t.cell(row, "제안CBM"));
			if (name.isEmpty() || size == null || cbm == null) {
				continue;
			}
			ItemStandard s = new ItemStandard(name, t.cell(row, "카테고리"), size, t.cell(row, "크기명"), t.cell(row, "판정기준"),
					cbm, decimal(t.cell(row, "무게kg")), t.cell(row, "옵션코드"), t.cell(row, "근거"));
			list.add(s);
			byKey.put(s.name() + "|" + s.sizeCode(), s);
		}
		return new Snapshot(List.copyOf(list), Map.copyOf(byKey), List.of(), List.of(), source, LocalDateTime.now(), null);
	}

	/** 별칭,품목명,크기코드,비고 */
	static List<Alias> parseAliases(String text) {
		Table t = table(text, "별칭", "품목명");
		List<Alias> list = new ArrayList<>();
		for (List<String> row : t.rows()) {
			String alias = t.cell(row, "별칭");
			String item = t.cell(row, "품목명");
			if (!alias.isEmpty() && !item.isEmpty()) {
				String size = ItemStandard.sizeCodeOf(t.cell(row, "크기코드"));
				list.add(new Alias(alias, item, size == null ? "" : size, t.cell(row, "비고")));
			}
		}
		return List.copyOf(list);
	}

	/** 톤,…,적재CBM,… (톤 오름차순) */
	static List<Truck> parseTrucks(String text) {
		Table t = table(text, "톤", "적재CBM");
		List<Truck> list = new ArrayList<>();
		for (List<String> row : t.rows()) {
			BigDecimal ton = decimal(t.cell(row, "톤"));
			BigDecimal cbm = decimal(t.cell(row, "적재CBM"));
			if (ton != null && cbm != null && cbm.signum() > 0) {
				list.add(new Truck(ton, cbm));
			}
		}
		list.sort(Comparator.comparing(Truck::ton));
		return List.copyOf(list);
	}

	private record Table(Map<String, Integer> col, List<List<String>> rows) {

		String cell(List<String> row, String name) {
			Integer i = col.get(name);
			return i == null || i >= row.size() ? "" : row.get(i).trim();
		}
	}

	private static Table table(String text, String... required) {
		List<List<String>> rows = CsvReader.read(text);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("빈 파일");
		}
		Map<String, Integer> col = new HashMap<>();
		List<String> header = rows.get(0);
		for (int i = 0; i < header.size(); i++) {
			col.put(header.get(i).trim(), i);
		}
		for (String r : required) {
			if (!col.containsKey(r)) {
				throw new IllegalArgumentException("'" + r + "' 열이 없어요");
			}
		}
		return new Table(col, rows.subList(1, rows.size()));
	}

	private static BigDecimal decimal(String text) {
		try {
			return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
