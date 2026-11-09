package com.codemove.schedule;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 달 날짜 상태 (사용자 견적 날짜 선택 · 예약 일정에서 사용, 모두 날짜 순).
 * closed: 휴무(정기 휴무 요일 · 공휴일 쉬기 · 임시 휴무) / full: 마감(예약 확정 건수 ≥ 하루 최대 예약, closed 와 겹치면 closed 에만)
 * / holidays: 그 달 공휴일 전부(표시용)
 */
public record MonthAvailability(List<LocalDate> closed, List<LocalDate> full, List<LocalDate> holidays) {
}
