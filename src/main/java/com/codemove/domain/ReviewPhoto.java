package com.codemove.domain;

import lombok.Getter;
import lombok.Setter;

/** 후기 사진 (TBL_REVIEW_PHOTO). 후기당 최대 5장, SORT_ORDER 1~5 */
@Getter
@Setter
public class ReviewPhoto {

	private Long photoId;
	private Long reviewId;
	/** 서버 저장 경로 */
	private String filePath;
	private Integer sortOrder;
}
