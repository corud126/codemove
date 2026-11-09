package com.codemove.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 예약 (TBL_RESERVATION) */
@Getter
@Setter
public class Reservation {

	private Long reservationId;
	/** ME-이사일(YYYYMMDD)-NNNN */
	private String reservationNo;
	private Long requestId;
	/** RSV_STATUS: 01 예약 완료(확정) · 02 이사 완료 · 03 예약 취소 */
	private String status;
	/** PAY_STATUS: 01 결제 대기 · 02 결제 완료 · 03 결제 취소 */
	private String payStatus;
	/** 예약 생성 시 견적서 작업일·작업시각을 복사. 이후 일정 변경은 예약 쪽만 고친다 */
	private LocalDate moveDate;
	private String moveTime;
	private String moveMemo;
	private LocalDateTime appliedAt;
	private LocalDateTime depositPaidAt;
	private LocalDateTime cancelledAt;
	/** CANCELLED_BY: 01 회원 · 02 관리자 */
	private String cancelledBy;
}
