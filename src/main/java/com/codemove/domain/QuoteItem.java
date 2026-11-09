package com.codemove.domain;

import lombok.Getter;
import lombok.Setter;

/** 견적서 품목 (TBL_QUOTE_ITEM) */
@Getter
@Setter
public class QuoteItem {

	private Long qtItemId;
	private Long quoteId;
	/** ITEM_CATEGORY */
	private String category;
	private String itemName;
	private int quantity;
	private String unit;
	private int sortOrder;
}
