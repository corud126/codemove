package com.codemove.schedule;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.codemove.domain.ReservationSetting;

/**
 * 예약 설정 값 (TBL_RESERVATION_SETTING 을 풀어 둔 것). 행이 없으면 기본값: 하루 3건 · 정기 휴무 없음 · 공휴일에 예약 받음.
 * closedWeekdays: 0 일 ~ 6 토
 */
public record ScheduleSetting(int dailyCapacity, Set<Integer> closedWeekdays, boolean holidayClosed) {

	public static final int DEFAULT_CAPACITY = 3;
	public static final int MIN_CAPACITY = 1;
	public static final int MAX_CAPACITY = 10;

	public static final ScheduleSetting DEFAULT = new ScheduleSetting(DEFAULT_CAPACITY, Set.of(), false);

	public ScheduleSetting {
		closedWeekdays = Set.copyOf(new TreeSet<>(closedWeekdays));
	}

	public static ScheduleSetting of(ReservationSetting row) {
		if (row == null) {
			return DEFAULT;
		}
		int capacity = row.getDailyCapacity() != null ? row.getDailyCapacity() : DEFAULT_CAPACITY;
		return new ScheduleSetting(capacity, parseWeekdays(row.getClosedWeekdays()), "Y".equals(row.getHolidayClosed()));
	}

	/** '0,1' → {0, 1}. 0~6 밖의 값·빈칸은 무시 */
	public static Set<Integer> parseWeekdays(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> s.matches("[0-6]"))
				.map(Integer::valueOf).collect(Collectors.toCollection(TreeSet::new));
	}

	/** 정렬된 요일 목록 */
	public List<Integer> weekdayList() {
		return closedWeekdays.stream().sorted().toList();
	}

	/** DB 저장용 '0,1' (없으면 null) */
	public String weekdaysCsv() {
		return closedWeekdays.isEmpty() ? null
				: weekdayList().stream().map(String::valueOf).collect(Collectors.joining(","));
	}
}
