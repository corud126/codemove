package com.codemove.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 견적 요청 (TBL_MOVE_REQUEST) */
@Getter
@Setter
public class MoveRequest {

	private Long requestId;
	private Long memberId;
	/** HOUSE_TYPE */
	private String houseType;
	/** PACK_TYPE */
	private String packType;
	/** 날짜 미정이면 MOVE_DATE · PREFER_TIME 모두 NULL */
	private LocalDate moveDate;
	/** PREFER_TIME: 01 오전 · 02 오후 · 03 상관없음 */
	private String preferTime;
	private String fromAddress;
	private String fromDetail;
	private String toAddress;
	private String toDetail;
	/** 품목 VOLUME 합계 */
	private BigDecimal aiCbm;
	private Long aiEstAmount;
	private Long aiEstMin;
	private Long aiEstMax;
	private Integer aiAnalysisSec;
	private Integer aiConfidence;
	private LocalDateTime requestedAt;
}
