package com.codemove.domain;

import lombok.Getter;
import lombok.Setter;

/** 견적서 옵션 (TBL_QUOTE_OPTION) — 옵션 8종(QUOTE_OPTION) × 선택 2칸 + 비용 */
@Getter
@Setter
public class QuoteOption {

	private Long qtOptionId;
	private Long quoteId;
	/** QUOTE_OPTION: 01 피아노 ~ 08 에어컨 */
	private String optionCode;
	private String choice1Yn;
	private String choice2Yn;
	private long optionCost;
}
