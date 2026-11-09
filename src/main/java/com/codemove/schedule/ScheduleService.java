package com.codemove.schedule;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.ClosedDay;
import com.codemove.domain.ReservationSetting;

import lombok.RequiredArgsConstructor;

/**
 * 일정 관리(SCR-A-022) 설정 · 임시 휴무 저장과 예약 일정(SCR-A-020) 조회.
 * 저장할 때 ADMIN_ID · UPDATED_AT 을 남긴다(화면에는 표시하지 않음). 자동 알림 없음(Q23).
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

	private final ScheduleMapper scheduleMapper;

	/** 화면에 보여 줄 오류 (잘못된 입력) */
	public static class ScheduleException extends RuntimeException {
		public ScheduleException(String message) {
			super(message);
		}
	}

	/** 예약 설정 (행이 없으면 기본값) */
	public ScheduleSetting setting() {
		return ScheduleSetting.of(scheduleMapper.selectSetting());
	}

	/** 예약 설정 저장. 행이 없으면 만든다 */
	@Transactional
	public ScheduleSetting saveSetting(int dailyCapacity, Set<Integer> closedWeekdays, boolean holidayClosed, Long adminId) {
		if (dailyCapacity < ScheduleSetting.MIN_CAPACITY || dailyCapacity > ScheduleSetting.MAX_CAPACITY) {
			throw new ScheduleException("하루 최대 예약은 1~10건으로 정해 주세요.");
		}
		if (closedWeekdays.stream().anyMatch(d -> d == null || d < 0 || d > 6)) {
			throw new ScheduleException("정기 휴무 요일을 다시 확인해 주세요.");
		}
		if (closedWeekdays.size() == 7) {
			throw new ScheduleException("모든 요일을 쉬는 날로 정할 수는 없어요.");
		}
		ScheduleSetting saved = new ScheduleSetting(dailyCapacity, closedWeekdays, holidayClosed);
		ReservationSetting row = scheduleMapper.selectSetting();
		boolean isNew = row == null;
		if (isNew) {
			row = new ReservationSetting();
		}
		row.setDailyCapacity(saved.dailyCapacity());
		row.setClosedWeekdays(saved.weekdaysCsv());
		row.setHolidayClosed(saved.holidayClosed() ? "Y" : "N");
		row.setAdminId(adminId);
		if (isNew) {
			scheduleMapper.insertSetting(row);
		} else {
			scheduleMapper.updateSetting(row);
		}
		return saved;
	}

	/** 임시 휴무 전체 (날짜 순) */
	public List<ClosedDay> closedDays() {
		return scheduleMapper.selectAllClosedDays();
	}

	/**
	 * 임시 휴무 등록 · 날짜 변경 (before 가 있으면 그 날짜를 지우고 date 로 옮김).
	 * 오늘 이전 날짜 · 이미 지정한 날짜는 막는다. 그날 예약 확정(01) 건수를 돌려준다(경고는 화면이 미리 보여 줌, Q23).
	 */
	@Transactional
	public int addClosedDay(LocalDate date, LocalDate before, Long adminId) {
		if (date == null) {
			throw new ScheduleException("날짜를 골라 주세요.");
		}
		if (date.isBefore(LocalDate.now())) {
			throw new ScheduleException("지난 날짜는 휴무일로 정할 수 없어요.");
		}
		if (!date.equals(before)) {
			if (scheduleMapper.countClosedDay(date) > 0) {
				throw new ScheduleException("이미 휴무일로 지정된 날짜예요.");
			}
			if (before != null) {
				scheduleMapper.deleteClosedDay(before);
			}
			ClosedDay day = new ClosedDay();
			day.setClosedDate(date);
			day.setAdminId(adminId);
			scheduleMapper.insertClosedDay(day);
		}
		return confirmedCount(date);
	}

	/** 임시 휴무 해제 (행 삭제). 없던 날짜면 false */
	@Transactional
	public boolean removeClosedDay(LocalDate date) {
		return date != null && scheduleMapper.deleteClosedDay(date) > 0;
	}

	/** 그날 예약 확정(01) 건수 */
	public int confirmedCount(LocalDate date) {
		return confirmedCounts(date, date.plusDays(1)).getOrDefault(date, 0);
	}

	/** 날짜별 예약 확정(01) 건수 [from, to) */
	public Map<LocalDate, Integer> confirmedCounts(LocalDate from, LocalDate to) {
		Map<LocalDate, Integer> counts = new LinkedHashMap<>();
		for (DayCount c : scheduleMapper.countConfirmedByDay(from, to)) {
			counts.put(c.getMoveDay(), c.getCnt());
		}
		return counts;
	}

	/** 예약 일정 달력: 예약 완료(01) · 이사 완료(02) [from, to) */
	public List<CalendarReservation> calendarReservations(LocalDate from, LocalDate to) {
		return scheduleMapper.selectCalendarReservations(from, to);
	}
}
