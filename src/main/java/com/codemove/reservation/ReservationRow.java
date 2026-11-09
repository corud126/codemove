package com.codemove.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 예약 + 견적 요청 · 회원 · 견적서를 묶은 조회 행 (관리자 예약 관리 목록, 사용자 예약내역).
 * 주소·금액은 견적서(TBL_QUOTE) 값, 이사 유형은 견적 요청(TBL_MOVE_REQUEST) 값.
 */
@Getter
@Setter
public class ReservationRow {

	private Long reservationId;
	private String reservationNo;
	private String status;
	private String payStatus;
	private LocalDate moveDate;
	private String moveTime;
	private String moveMemo;
	private LocalDateTime appliedAt;
	private LocalDateTime depositPaidAt;
	private LocalDateTime cancelledAt;
	private String cancelledBy;

	private String memberName;
	private String phone;

	private String quoteNo;
	/** HOUSE_TYPE (견적 요청) */
	private String houseType;
	/** PACK_TYPE (견적 요청) */
	private String packType;
	/** 견적서 작업내용 PACK_TYPE — 있으면 요청 포장 방식보다 우선 */
	private String workType;
	private String fromAddress;
	private String toAddress;
	private Long totalAmount;
	private Long depositAmount;
	private Long balanceAmount;
}
