package com.codemove.schedule;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 예약 일정 달력에 보여 줄 예약 1건 (TBL_RESERVATION + 견적 요청 · 회원 · 견적서 조인) */
@Getter
@Setter
public class CalendarReservation {

	private Long reservationId;
	private String reservationNo;
	/** RSV_STATUS 01 예약 완료 · 02 이사 완료 (03 취소는 조회하지 않음) */
	private String status;
	/** PAY_STATUS */
	private String payStatus;
	private LocalDate moveDate;
	/** HH:mm, 정하지 않았으면 NULL */
	private String moveTime;
	private String memberName;
	private String phone;
	/** HOUSE_TYPE (이사 유형) */
	private String houseType;
	private String fromAddress;
	private String toAddress;
}
