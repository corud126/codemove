package com.codemove.common.holiday;

import java.time.LocalDate;

/** 공휴일 하루 (외부 API · 내장 목록, DB 저장 없음 Q17) */
public record Holiday(LocalDate date, String name) {
}
