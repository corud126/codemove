package com.codemove.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 견적서 (TBL_QUOTE) */
@Getter
@Setter
public class Quote {

	private Long quoteId;
	/** QT-YYYYMMDD-NNNN */
	private String quoteNo;
	private Long requestId;
	private Long memberId;
	private Long adminId;
	/** QUOTE_SOURCE: 01 견적 요청 · 02 사진 등록 · 03 PDF 등록 */
	private String sourceType;
	/** QUOTE_STATUS: 01 견적 검토 · 02 견적 승인 · 03 견적 확정 · 04 만료 */
	private String status;
	private String companyName;
	/** 고객 이름·연락처 (견적서 '담당자 / TEL') */
	private String contactName;
	private String contactPhone;
	private LocalDate estimateDate;
	private String fromAddress;
	private String toAddress;
	private LocalDate workDate;
	/** 08:00 · 10:00 · 13:00 · 15:00 · 17:00 */
	private String workTime;
	private LocalDate transportDate;
	/** PACK_TYPE 코드 */
	private String workType;
	private String fromLadderUse;
	private String toLadderUse;
	private String fromElevatorUse;
	private String toElevatorUse;
	private String fromStairsUse;
	private String toStairsUse;
	private BigDecimal volumeTon;
	private String note;
	private Long movingCost;
	private Integer fromLadderFloor;
	private Long fromLadderCost;
	private Integer toLadderFloor;
	private Long toLadderCost;
	private Long totalAmount;
	private Long depositAmount;
	private Long balanceAmount;
	private String documentPath;
	private LocalDateTime approvedAt;
	private LocalDateTime expiresAt;
	private LocalDateTime updatedAt;
}
