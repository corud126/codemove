package com.codemove.request;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 견적 요청 설정 (application.yml codemove.quote).
 * 톤 환산은 ERD 임시 규칙(revision-user.md 'CBM → 톤 환산 규칙'), 단가는 업체 기준이 정해질 때까지 임시값.
 */
@ConfigurationProperties("codemove.quote")
public record QuoteProperties(
		/** 영상 저장 폴더 (git 제외) */
		String uploadDir,
		/** 1톤에 싣는 CBM */
		BigDecimal cbmPerTon,
		/** 톤 올림 단위 */
		BigDecimal tonStep,
		BigDecimal minTon,
		/** 계약금 비율 */
		BigDecimal depositRate,
		/** AI 품목 신뢰도가 이 값 미만이면 '확인 필요' */
		int reviewConfidence,
		Pricing pricing) {

	/** 예상 금액 = (기본 요금 + 톤당 요금 × 톤) × 포장 방식 배율. 날짜 미정이면 범위로 보여준다 */
	public record Pricing(long baseFee, long perTon, BigDecimal partialPackRate,
			BigDecimal rangeMinRate, BigDecimal rangeMaxRate, long roundUnit) {
	}
}
