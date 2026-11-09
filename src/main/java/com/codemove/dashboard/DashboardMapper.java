package com.codemove.dashboard;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 홈(대시보드) 집계 — 읽기 전용 */
@Mapper
public interface DashboardMapper {

	/** 예약 건수. statuses 가 비어 있으면 전체 (RSV_STATUS 01 예약 완료 · 02 이사 완료 · 03 예약 취소) */
	int countReservations(@Param("statuses") List<String> statuses);

	int countReviews();

	/** 견적 상태별 건수 (QUOTE_STATUS) */
	int countQuotes(String status);

	/** 기간 [from, to) 에 접수된(APPLIED_AT) 예약의 날짜별 건수 */
	List<DailyCount> countAppliedByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 최근 접수된 예약 (접수 최근 순) */
	List<RecentReservation> selectRecentReservations(int limit);
}
