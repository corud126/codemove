package com.codemove.notification;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** D-3 이사 임박 알림 대상 (TBL_RESERVATION + TBL_MOVE_REQUEST, 읽기 전용) */
@Getter
@Setter
public class MoveReminder {

	private Long reservationId;
	private Long memberId;
	private LocalDate moveDate;
	/** "10:00" */
	private String moveTime;
}
