package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 후기 (TBL_REVIEW). 예약 1건당 1개 (UK_REVIEW_RESERVATION) */
@Getter
@Setter
public class Review {

	private Long reviewId;
	private Long reservationId;
	/** 별점 1~5 */
	private Integer rating;
	/** 후기 내용 (최대 500자) */
	private String content;
	private LocalDateTime createdAt;
	/** 사장님 답변 (최대 500자). 없으면 답변 대기 */
	private String replyContent;
	private LocalDateTime repliedAt;
	/** 답변한 관리자 */
	private Long adminId;
}
