package com.codemove.videoanalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 표준 품목 기준표 (품목기준표.csv, 경로 codemove.video-analysis.standard-csv).
 * 검증 단계라 DB 가 아니라 파일에서 읽는다: 기준값을 고치고 [다시 읽기]만 하면 바로 다시 테스트할 수 있다.
 * 열은 헤더 이름으로 찾는다 (품목명 · 카테고리 · 크기코드 · 크기명 · 판정기준 · 제안CBM · 무게kg · 옵션코드 · 근거).
 */
@Slf4j
@Component
public class ItemStandardCatalog {

	/** 카테고리 코드(ITEM_CATEGORY) → 이름 */
	public static final Map<String, String> CATEGORIES = Map.of("01", "가구", "02", "가전", "03", "생활용품", "04", "기타");

	private final Path csv;
	private volatile Snapshot snapshot;

	/** list: 파일 순서 · error: 읽기 실패 사유(없으면 null) */
	public record Snapshot(List<ItemStandard> list, Map<String, ItemStandard> byKey, String source, LocalDateTime loadedAt,
			String error) {

		public boolean ready() {
			return error == null && !list.isEmpty();
		}

		/** 품목 이름 목록 (중복 없이, 파일 순서) */
		public List<String> names() {
			return list.stream().map(ItemStandard::name).distinct().toList();
		}
	}

	public ItemStandardCatalog(@Value("${codemove.video-analysis.standard-csv:}") String csvPath) {
		this.csv = csvPath == null || csvPath.isBlank() ? null : Path.of(csvPath.trim());
		reload();
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	/** 파일을 다시 읽는다. 실패해도 예외 대신 error 가 채워진 빈 기준표 */
	public synchronized Snapshot reload() {
		if (csv == null) {
			snapshot = empty("기준표 경로(codemove.video-analysis.standard-csv)가 설정되지 않았어요.");
		} else if (!Files.isRegularFile(csv)) {
			snapshot = empty("기준표 파일이 없어요: " + csv);
		} else {
			try {
				snapshot = parse(Files.readString(csv, StandardCharsets.UTF_8), csv.toString());
			} catch (IOException | IllegalArgumentException e) {
				log.warn("품목 기준표 읽기 실패: {}", csv, e);
				snapshot = empty("기준표를 읽지 못했어요: " + e.getMessage());
			}
		}
		return snapshot;
	}

	public ItemStandard find(String name, String sizeCode) {
		return snapshot.byKey().get(key(name, sizeCode));
	}

	private Snapshot empty(String error) {
		return new Snapshot(List.of(), Map.of(), csv == null ? "" : csv.toString(), LocalDateTime.now(), error);
	}

	/** CSV 본문 → 기준표 (테스트에서도 쓴다). 필수 열이 없으면 IllegalArgumentException */
	static Snapshot parse(String text, String source) {
		List<List<String>> rows = CsvReader.read(text);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("빈 파일");
		}
		Map<String, Integer> col = new HashMap<>();
		List<String> header = rows.get(0);
		for (int i = 0; i < header.size(); i++) {
			col.put(header.get(i).trim(), i);
		}
		for (String required : List.of("품목명", "카테고리", "크기코드", "제안CBM")) {
			if (!col.containsKey(required)) {
				throw new IllegalArgumentException("'" + required + "' 열이 없어요");
			}
		}
		List<ItemStandard> list = new ArrayList<>();
		Map<String, ItemStandard> byKey = new LinkedHashMap<>();
		for (int r = 1; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			String name = cell(row, col, "품목명");
			String size = cell(row, col, "크기코드");
			BigDecimal cbm = decimal(cell(row, col, "제안CBM"));
			if (name.isEmpty() || ItemStandard.sizeCodeOf(size) == null || cbm == null) {
				continue;
			}
			ItemStandard s = new ItemStandard(name, cell(row, col, "카테고리"), ItemStandard.sizeCodeOf(size),
					cell(row, col, "크기명"), cell(row, col, "판정기준"), cbm, decimal(cell(row, col, "무게kg")),
					cell(row, col, "옵션코드"), cell(row, col, "근거"));
			list.add(s);
			byKey.put(key(s.name(), s.sizeCode()), s);
		}
		return new Snapshot(List.copyOf(list), Map.copyOf(byKey), source, LocalDateTime.now(), null);
	}

	private static String key(String name, String sizeCode) {
		return name + "|" + sizeCode;
	}

	private static String cell(List<String> row, Map<String, Integer> col, String name) {
		Integer i = col.get(name);
		return i == null || i >= row.size() ? "" : row.get(i).trim();
	}

	private static BigDecimal decimal(String text) {
		try {
			return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
