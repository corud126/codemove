package com.codemove.schedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.codemove.common.holiday.Holiday;
import com.codemove.common.holiday.HolidayProvider;
import com.codemove.domain.ClosedDay;

import lombok.RequiredArgsConstructor;

/**
 * 날짜별 예약 가능 여부 (revision-user.md Q18 · 4단계 규칙).
 * 휴무 = 정기 휴무 요일 + 공휴일(공휴일에 쉬기 켬) + 임시 휴무(TBL_CLOSED_DAY).
 * 마감 = 그날 예약 확정(RSV_STATUS 01) 건수 ≥ 하루 최대 예약. 이사 완료(02) · 취소(03) · 견적 요청은 세지 않는다.
 * 손 없는 날은 표시만 하고 막지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

	private final ScheduleMapper scheduleMapper;
	private final HolidayProvider holidayProvider;

	/** closed: 정기 휴무 요일 · (HOLIDAY_CLOSED=Y 이면) 공휴일 · TBL_CLOSED_DAY. full: 그날 확정 예약 수 >= DAILY_CAPACITY (closed 와 겹치면 closed 에만). holidays: 그 달 공휴일 전부(표시용) */
	public MonthAvailability month(YearMonth month) {
		return month(month, ScheduleSetting.of(scheduleMapper.selectSetting()));
	}

	/** 오늘 이후이고 closed·full 이 아니면 true */
	public boolean isAvailable(LocalDate date) {
		if (date == null || !date.isAfter(LocalDate.now())) {
			return false;
		}
		MonthAvailability m = month(YearMonth.from(date));
		return !m.closed().contains(date) && !m.full().contains(date);
	}

	/**
	 * 예약 확정(예약 생성) 직전 확인 — 반드시 쓰기 트랜잭션 안에서 부른다 (Q18 동시 확정 처리).
	 * 설정 행을 SELECT … FOR UPDATE 로 잠근 뒤 그날 상태를 다시 계산하므로, 같은 트랜잭션에서 예약을 넣으면 동시에 확정해도 하루 최대 예약을 넘지 않는다.
	 * (설정 행이 아직 없으면 잠글 행이 없어 기본값으로만 계산한다)
	 */
	public boolean isAvailableForConfirm(LocalDate date) {
		if (date == null || !date.isAfter(LocalDate.now())) {
			return false;
		}
		ScheduleSetting setting = ScheduleSetting.of(scheduleMapper.selectSettingForUpdate());
		MonthAvailability m = month(YearMonth.from(date), setting);
		return !m.closed().contains(date) && !m.full().contains(date);
	}

	private MonthAvailability month(YearMonth month, ScheduleSetting setting) {
		LocalDate from = month.atDay(1);
		LocalDate to = month.plusMonths(1).atDay(1);

		List<LocalDate> holidays = holidayProvider.holidays(month).stream().map(Holiday::date).toList();
		Set<LocalDate> holidaySet = new HashSet<>(holidays);
		Set<LocalDate> closedDays = new HashSet<>();
		for (ClosedDay d : scheduleMapper.selectClosedDays(from, to)) {
			closedDays.add(d.getClosedDate());
		}
		Map<LocalDate, Integer> counts = new HashMap<>();
		for (DayCount c : scheduleMapper.countConfirmedByDay(from, to)) {
			counts.put(c.getMoveDay(), c.getCnt());
		}

		List<LocalDate> closed = new ArrayList<>();
		List<LocalDate> full = new ArrayList<>();
		for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
			int weekday = d.getDayOfWeek().getValue() % 7; // 0 일 ~ 6 토
			boolean isClosed = setting.closedWeekdays().contains(weekday)
					|| (setting.holidayClosed() && holidaySet.contains(d))
					|| closedDays.contains(d);
			if (isClosed) {
				closed.add(d);
			} else if (counts.getOrDefault(d, 0) >= setting.dailyCapacity()) {
				full.add(d);
			}
		}
		return new MonthAvailability(List.copyOf(closed), List.copyOf(full), holidays);
	}
}
