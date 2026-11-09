package com.codemove.review;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 후기 조회 행 (TBL_REVIEW + 예약 · 견적 요청 · 회원 · 견적서). 후기 작성 대상 예약을 찾을 때는 후기 컬럼이 비어 있다.
 * 포장 방식 · 주소는 견적서(TBL_QUOTE)에 있으면 그것, 없으면 견적 요청 값
 */
@Getter
@Setter
public class ReviewRow {

	private Long reviewId;
	private Long reservationId;
	private String reservationNo;
	/** RSV_STATUS */
	private String reservationStatus;
	private Long memberId;
	private String memberName;
	private Integer rating;
	private String content;
	private LocalDateTime createdAt;
	private String replyContent;
	private LocalDateTime repliedAt;
	/** HOUSE_TYPE */
	private String houseType;
	/** PACK_TYPE (견적서 작업내용 우선) */
	private String packType;
	private String fromAddress;
	private String toAddress;
	private LocalDate moveDate;
	/** 이 이사까지 회원의 이사 완료 횟수 (홈 '이용 N회차') */
	private Integer useCount;
}
