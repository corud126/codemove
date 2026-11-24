package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.List;

/** 큰따옴표로 감싼 칸(쉼표 · 줄바꿈 · "" 포함)을 지원하는 최소 CSV 읽기. 앞의 UTF-8 BOM 은 건너뛴다 */
final class CsvReader {

	private CsvReader() {
	}

	static List<List<String>> read(String text) {
		String s = text.startsWith("﻿") ? text.substring(1) : text;
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < s.length() && s.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
				} else {
					cell.append(c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(cell.toString());
				cell.setLength(0);
				if (!(row.size() == 1 && row.get(0).isEmpty())) {
					rows.add(row);
				}
				row = new ArrayList<>();
			} else {
				cell.append(c);
			}
		}
		row.add(cell.toString());
		if (!(row.size() == 1 && row.get(0).isEmpty())) {
			rows.add(row);
		}
		return rows;
	}
}
