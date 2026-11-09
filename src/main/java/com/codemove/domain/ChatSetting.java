package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 채팅 운영 설정 (TBL_CHAT_SETTING, 한 줄만 둔다) */
@Getter
@Setter
public class ChatSetting {

	private Long settingId;
	private String botEnabled;
	/** 상담 운영(상담원 연결 받기). 끄면 운영 시간이어도 부재 안내 */
	private String consultEnabled;
	/** "09:00" */
	private String startTime;
	private String endTime;
	private String greeting;
	/** 상담 대기가 길어질 때 자동 발송 */
	private String delayMessage;
	/** 운영 시간 밖 · 상담 운영 꺼짐일 때 */
	private String awayMessage;
	private LocalDateTime updatedAt;
	private Long adminId;

	public boolean isBotOn() {
		return !"N".equals(botEnabled);
	}

	public boolean isConsultOn() {
		return !"N".equals(consultEnabled);
	}
}
