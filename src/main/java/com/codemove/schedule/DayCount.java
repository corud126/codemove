package com.codemove.schedule;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 날짜별 예약 확정 건수 */
@Getter
@Setter
public class DayCount {

	private LocalDate moveDay;
	private int cnt;
}
