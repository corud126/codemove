package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 예약 설정 (TBL_RESERVATION_SETTING, 1행) — 일정 관리 > 예약 받기 · 쉬는 날 */
@Getter
@Setter
public class ReservationSetting {

	private Long settingId;
	/** 하루 최대 예약 1~10 (기본 3). 그날 예약 확정(01) 건수가 닿으면 마감 */
	private Integer dailyCapacity;
	/** 정기 휴무 요일, 쉼표 구분 (0 일 ~ 6 토). 없으면 NULL */
	private String closedWeekdays;
	/** 공휴일에 쉬기 Y/N */
	private String holidayClosed;
	private LocalDateTime updatedAt;
	private Long adminId;
}
