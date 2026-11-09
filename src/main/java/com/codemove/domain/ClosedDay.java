package com.codemove.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 임시 휴무일 (TBL_CLOSED_DAY, 하루 1행 · 해제 = 행 삭제) */
@Getter
@Setter
public class ClosedDay {

	private LocalDate closedDate;
	private LocalDateTime updatedAt;
	private Long adminId;
}
