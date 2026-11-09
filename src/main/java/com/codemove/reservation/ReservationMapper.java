package com.codemove.reservation;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.Reservation;

@Mapper
public interface ReservationMapper {

	/** RESERVATION_ID 는 SEQ_RESERVATION, RESERVATION_NO 는 ME-이사일-RESERVATION_ID 끝 4자리 */
	int insertReservation(Reservation reservation);

	Reservation selectByRequestId(Long requestId);

	Reservation selectById(Long reservationId);

	/** 회원의 예약 (이사일 순) */
	List<Reservation> selectByMember(Long memberId);

	/** 회원 본인 예약만 (아니면 null) */
	Reservation selectForMember(@Param("reservationId") Long reservationId, @Param("memberId") Long memberId);

	int updateMemo(@Param("reservationId") Long reservationId, @Param("memo") String memo);

	/** 예약 취소: 예약 완료(01)만. 결제 대기였으면 결제 취소(03) (ERD 2단계) */
	int cancel(@Param("reservationId") Long reservationId, @Param("cancelledBy") String cancelledBy);

	/** 이사일이 지난 예약 완료 → 이사 완료 (ERD Q7: 이사일 다음 날 0시) */
	int completeOverdue();

	/** 후기를 남겼는지 (예약 1건당 후기 1개) */
	boolean hasReview(Long reservationId);
}
