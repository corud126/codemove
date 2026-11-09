package com.codemove.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 견적 요청 품목 (TBL_REQUEST_ITEM) */
@Getter
@Setter
public class RequestItem {

	private Long reItemId;
	private Long requestId;
	/** ITEM_CATEGORY */
	private String category;
	private String itemName;
	/** ITEM_SIZE: 01 소형 · 02 중형 · 03 대형 */
	private String itemSize;
	private int quantity;
	/** 수량을 반영한 총 부피 = 기준부피 × 크기계수 × 수량 */
	private BigDecimal volume;
	/** AI 인식 신뢰도 0~100. 사용자 추가 품목은 NULL */
	private Integer confidence;
	private String needsReview;
	/** ITEM_SOURCE: 01 AI 인식 · 02 사용자 추가 */
	private String source;
}
