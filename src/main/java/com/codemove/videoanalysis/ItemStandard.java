package com.codemove.videoanalysis;

import java.math.BigDecimal;

/**
 * 표준 품목 규격 한 줄 (품목 · 크기 등급별). 품목기준표.csv 한 행.
 * cbm: 1개 기준 부피(m³) · weightKg: 1개 기준 무게(kg, 조사 전이면 null) · basis: 값의 근거(조사값 · 가정값)
 */
public record ItemStandard(String name, String category, String sizeCode, String sizeName, String guide,
		BigDecimal cbm, BigDecimal weightKg, String optionCode, String basis) {

	/** 크기 코드(ITEM_SIZE) → 이름 */
	public static String sizeLabel(String sizeCode) {
		return switch (sizeCode == null ? "" : sizeCode) {
			case "01" -> "소형";
			case "02" -> "중형";
			case "03" -> "대형";
			default -> "";
		};
	}

	/** 소형 · 중형 · 대형 (또는 01~03) → 크기 코드. 모르면 null */
	public static String sizeCodeOf(String grade) {
		if (grade == null) {
			return null;
		}
		return switch (grade.trim()) {
			case "소형", "소", "01" -> "01";
			case "중형", "중", "02" -> "02";
			case "대형", "대", "03" -> "03";
			default -> null;
		};
	}
}
