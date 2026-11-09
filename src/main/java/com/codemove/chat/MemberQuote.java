package com.codemove.chat;

import lombok.Getter;
import lombok.Setter;

/** 챗봇 '견적 조회' 답변 · 관리자 고객 정보 시트용 견적 요약 (TBL_QUOTE, 읽기 전용) */
@Getter
@Setter
public class MemberQuote {

	private Long quoteId;
	private String quoteNo;
	/** QUOTE_STATUS */
	private String status;
	private Long totalAmount;
}
