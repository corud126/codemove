package com.codemove.chat;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.ChatSetting;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 운영 설정 (TBL_CHAT_SETTING 한 줄).
 * 아직 저장한 적이 없으면 기본값을 쓰고, 처음 저장할 때 행을 만든다.
 * 운영 시간은 평일(월~금) START_TIME ~ END_TIME (고정 규칙: 평일 운영).
 */
@Service
@RequiredArgsConstructor
public class ChatSettingService {

	public static final String DEFAULT_GREETING = "안녕하세요, 명품익스프레스입니다.\n궁금하신 내용을 선택해주세요.";
	static final int MESSAGE_MAX = 500;

	private final ChatMapper chatMapper;

	public ChatSetting get() {
		ChatSetting setting = chatMapper.selectSetting();
		return setting != null ? setting : defaults();
	}

	/** 운영 설정 저장 (인사말은 채팅 시작 화면에서 따로) */
	@Transactional
	public ChatSetting saveOperation(boolean botOn, boolean consultOn, String startTime, String endTime,
			String delayMessage, String awayMessage, Long adminId) {
		if (!isTime(startTime) || !isTime(endTime)) {
			throw new IllegalArgumentException("운영 시간을 다시 확인해 주세요.");
		}
		if (startTime.compareTo(endTime) >= 0) {
			throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 해요.");
		}
		ChatSetting setting = get();
		setting.setBotEnabled(botOn ? "Y" : "N");
		setting.setConsultEnabled(consultOn ? "Y" : "N");
		setting.setStartTime(startTime);
		setting.setEndTime(endTime);
		setting.setDelayMessage(optionalMessage(delayMessage));
		setting.setAwayMessage(optionalMessage(awayMessage));
		return store(setting, adminId);
	}

	/** 채팅 시작 화면의 인사말 */
	@Transactional
	public ChatSetting saveGreeting(String greeting, Long adminId) {
		String g = greeting == null ? "" : greeting.strip();
		if (g.isEmpty() || g.length() > MESSAGE_MAX) {
			throw new IllegalArgumentException("인사말을 다시 확인해 주세요.");
		}
		ChatSetting setting = get();
		setting.setGreeting(g);
		return store(setting, adminId);
	}

	/** 지금 상담원이 응대하는 시간인지 (상담 운영 꺼짐이면 false) */
	public boolean isConsultOpen(ChatSetting s, LocalDateTime now) {
		if (!s.isConsultOn()) {
			return false;
		}
		DayOfWeek day = now.getDayOfWeek();
		if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
			return false;
		}
		LocalTime t = now.toLocalTime();
		return !t.isBefore(LocalTime.parse(s.getStartTime())) && t.isBefore(LocalTime.parse(s.getEndTime()));
	}

	/** 고객 채팅 첫 화면 표기 "평일 09:00–18:00" */
	public static String hoursText(ChatSetting s) {
		return "평일 " + s.getStartTime() + "–" + s.getEndTime();
	}

	private ChatSetting store(ChatSetting setting, Long adminId) {
		setting.setAdminId(adminId);
		if (setting.getSettingId() == null) {
			chatMapper.insertSetting(setting);
		} else {
			chatMapper.updateSetting(setting);
		}
		return setting;
	}

	private static String optionalMessage(String raw) {
		String m = raw == null ? "" : raw.strip();
		if (m.length() > MESSAGE_MAX) {
			throw new IllegalArgumentException("안내 메시지는 " + MESSAGE_MAX + "자까지 입력할 수 있어요.");
		}
		return m.isEmpty() ? null : m;
	}

	private static boolean isTime(String v) {
		return v != null && v.matches("([01]\\d|2[0-3]):[0-5]\\d");
	}

	static ChatSetting defaults() {
		ChatSetting s = new ChatSetting();
		s.setBotEnabled("Y");
		s.setConsultEnabled("Y");
		s.setStartTime("09:00");
		s.setEndTime("18:00");
		s.setGreeting(DEFAULT_GREETING);
		return s;
	}
}
