package com.codemove.dashboard;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 날짜별 건수 */
@Getter
@Setter
public class DailyCount {

	private LocalDate appliedDay;
	private int appliedCount;
}
