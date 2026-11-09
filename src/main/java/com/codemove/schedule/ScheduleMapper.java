package com.codemove.schedule;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.ClosedDay;
import com.codemove.domain.ReservationSetting;

/** 예약 설정 · 임시 휴무 · 예약 조회(읽기 전용). 기간은 [from, to) */
@Mapper
public interface ScheduleMapper {

	/** 설정 1행 (없으면 null) */
	ReservationSetting selectSetting();

	/** 예약 확정 시 동시 처리용: 설정 행을 잠근다 (SELECT … FOR UPDATE, Q18) */
	ReservationSetting selectSettingForUpdate();

	/** SETTING_ID 는 SEQ_RESERVATION_SETTING */
	int insertSetting(ReservationSetting setting);

	int updateSetting(ReservationSetting setting);

	List<ClosedDay> selectClosedDays(@Param("from") LocalDate from, @Param("to") LocalDate to);

	List<ClosedDay> selectAllClosedDays();

	int countClosedDay(@Param("date") LocalDate date);

	int insertClosedDay(ClosedDay closedDay);

	int deleteClosedDay(@Param("date") LocalDate date);

	/** 날짜별 예약 확정(01) 건수 */
	List<DayCount> countConfirmedByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 예약 완료(01) · 이사 완료(02) 예약 (이사일 · 시각 순) */
	List<CalendarReservation> selectCalendarReservations(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
