package com.codemove.chat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 채팅 화면의 시각 표기.
 * 템플릿에서: ${@chatTimes.dayTime(room.startedAt)}
 */
@org.springframework.stereotype.Component("chatTimes")
public final class ChatTimes {

	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M.d");
	private static final DateTimeFormatter MD_HHMM = DateTimeFormatter.ofPattern("MM.dd HH:mm");


	/** 말풍선 시각 "14:02" */
	public static String hhmm(LocalDateTime t) {
		return t == null ? "" : t.format(HHMM);
	}

	/** 상담함 시간: 방금 · N분 전 · 오후 3:30 · 어제 · 월요일 · 10.23 */
	public static String listLabel(LocalDateTime t) {
		if (t == null) {
			return "";
		}
		LocalDateTime now = LocalDateTime.now();
		long minutes = Duration.between(t, now).toMinutes();
		if (minutes < 1) {
			return "방금";
		}
		if (minutes < 60) {
			return minutes + "분 전";
		}
		LocalDate day = t.toLocalDate();
		LocalDate today = now.toLocalDate();
		if (day.equals(today)) {
			return ampm(t);
		}
		if (day.equals(today.minusDays(1))) {
			return "어제";
		}
		if (day.isAfter(today.minusDays(7))) {
			return day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
		}
		return t.format(MD);
	}

	/** "오후 3:30" */
	public static String ampm(LocalDateTime t) {
		int h = t.getHour();
		return (h < 12 ? "오전 " : "오후 ") + (h % 12 == 0 ? 12 : h % 12) + ":" + String.format("%02d", t.getMinute());
	}

	/** 고객 정보 시트: 오늘이면 "오늘 14:02", 아니면 "11.02 16:40" */
	public static String dayTime(LocalDateTime t) {
		if (t == null) {
			return "";
		}
		return t.toLocalDate().equals(LocalDate.now()) ? "오늘 " + t.format(HHMM) : t.format(MD_HHMM);
	}

	/** 채팅방 날짜 줄: 오늘 · 어제 · 11월 2일 (월) */
	public static String dayLabel(LocalDate day) {
		LocalDate today = LocalDate.now();
		if (day.equals(today)) {
			return "오늘";
		}
		if (day.equals(today.minusDays(1))) {
			return "어제";
		}
		String label = day.getMonthValue() + "월 " + day.getDayOfMonth() + "일 ("
				+ day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
		return day.getYear() == today.getYear() ? label : day.getYear() + "년 " + label;
	}

	/** 대기 시간(분) */
	public static long minutesSince(LocalDateTime t) {
		return t == null ? 0 : Math.max(0, Duration.between(t, LocalDateTime.now()).toMinutes());
	}

	/** 정렬용 숫자 (초) */
	public static long epoch(LocalDateTime t) {
		return t == null ? 0 : t.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
	}
}
