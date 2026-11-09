package com.codemove.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 사용자 견적내역 조회 행 (TBL_QUOTE + 견적 요청의 이사 유형·포장 방식) */
@Getter
@Setter
public class QuoteHistoryRow {

	private Long quoteId;
	/** QUOTE_STATUS */
	private String status;
	private LocalDate estimateDate;
	private String fromAddress;
	private String toAddress;
	private LocalDate workDate;
	private Long totalAmount;
	private LocalDateTime expiresAt;
	private String workType;
	private String houseType;
	private String packType;
}
