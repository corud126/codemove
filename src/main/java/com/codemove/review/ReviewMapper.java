package com.codemove.review;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.Review;
import com.codemove.domain.ReviewPhoto;

@Mapper
public interface ReviewMapper {

	int insertReview(Review review);

	int insertPhoto(ReviewPhoto photo);

	Review selectById(Long reviewId);

	boolean existsByReservation(Long reservationId);

	/** 후기 목록 (최근 작성 순). memberId 가 null 이면 전체 (관리자) */
	List<ReviewRow> selectRows(@Param("memberId") Long memberId);

	/** 최근 후기 limit 건 (홈) */
	List<ReviewRow> selectLatest(@Param("limit") int limit);

	/** 사진 (후기 · 순서 순). memberId 가 null 이면 전체 */
	List<ReviewPhoto> selectPhotos(@Param("memberId") Long memberId);

	/** 사진 한 장. memberId 가 있으면 그 회원 후기의 사진만 */
	ReviewPhoto selectPhoto(@Param("photoId") Long photoId, @Param("memberId") Long memberId);

	/** 회원 본인 예약 정보 (후기 작성 화면). 아니면 null */
	ReviewRow selectReservation(@Param("reservationId") Long reservationId, @Param("memberId") Long memberId);

	/** 후기를 쓸 수 있는 예약: 이사 완료(02) · 후기 없음, 최근 이사 순 */
	List<ReviewRow> selectReviewable(@Param("memberId") Long memberId);

	int updateReply(@Param("reviewId") Long reviewId, @Param("content") String content, @Param("adminId") Long adminId);

	int deleteReply(@Param("reviewId") Long reviewId);
}
