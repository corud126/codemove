package com.codemove.chat;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 챗봇 '예약 조회' 답변 · 관리자 고객 정보 시트용 예약 요약 (TBL_RESERVATION + TBL_MOVE_REQUEST, 읽기 전용) */
@Getter
@Setter
public class MemberReservation {

	private Long reservationId;
	private String reservationNo;
	/** RSV_STATUS */
	private String status;
	private LocalDate moveDate;
	/** "10:00" */
	private String moveTime;
	private String fromAddress;
	private String toAddress;
}
