package com.codemove.dashboard;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 최근 예약 행: 회원 이름 · 이사일 · 출발/도착 주소(견적 요청) · 예약 상태 코드 */
@Getter
@Setter
public class RecentReservation {

	private Long reservationId;
	private String memberName;
	private LocalDate moveDate;
	private String fromAddress;
	private String toAddress;
	private String status;
}
