package com.codemove.common.holiday;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 특일 정보(한국천문연구원 SpcdeInfoService) 서비스 키 — codemove.holiday.service-key.
 * 비어 있으면 API 를 부르지 않고 내장 공휴일 목록(2026 · 2027)을 쓴다.
 */
@ConfigurationProperties("codemove.holiday")
public record HolidayProperties(String serviceKey) {

	public HolidayProperties {
		serviceKey = serviceKey != null ? serviceKey.trim() : "";
	}

	public boolean hasKey() {
		return !serviceKey.isEmpty();
	}
}
