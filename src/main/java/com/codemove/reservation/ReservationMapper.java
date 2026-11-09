package com.codemove.reservation;

import java.time.LocalDate;
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

	/* ---------- 관리자 예약 관리 (SCR-A-011) ---------- */

	/** 전체 예약 (이사일 최근 순) — 회원 · 견적서 연결 */
	List<ReservationRow> selectAdminRows();

	/** 관리자 [결제 완료]: 결제 대기(01)였던 예약 완료·이사 완료 건만 → 결제 완료(02) + DEPOSIT_PAID_AT */
	int completePayment(Long reservationId);

	/** 관리자 일정 변경: 예약 완료(01)만. 예약 번호(생성 시 이사일)는 그대로 둔다 */
	int updateSchedule(@Param("reservationId") Long reservationId, @Param("moveDate") LocalDate moveDate,
			@Param("moveTime") String moveTime);

	/* ---------- 사용자 견적내역 · 예약내역 ---------- */

	/** 회원의 예약 (이사일 최근 순) */
	List<ReservationRow> selectHistoryByMember(Long memberId);

	/** 회원의 견적 (최근 순) + 견적 요청의 이사 유형·포장 방식 */
	List<QuoteHistoryRow> selectQuoteHistory(Long memberId);
}
