package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 알림 설정 (TBL_NOTIFICATION_SETTING, 한 줄만 둔다).
 * 고객에게 휴대폰으로 보내는 메시지(알림톡·문자)에만 적용. 앱 안 알림 목록에는 항상 바로 저장된다 (ERD Q22)
 */
@Getter
@Setter
public class NotificationSetting {

	private Long settingId;
	/** 자동 발송 (끄면 알림톡·문자를 보내지 않음) */
	private String isActive;
	/** 야간 발송 보류 사용 */
	private String quietEnabled;
	/** 보류 시작 · 종료 "22:00" */
	private String quietStart;
	private String quietEnd;
	/** 알림톡 실패 시 문자 대체 */
	private String smsFallback;
	private LocalDateTime updatedAt;
	private Long adminId;

	public boolean isActiveOn() {
		return !"N".equals(isActive);
	}

	public boolean isQuietOn() {
		return !"N".equals(quietEnabled);
	}

	public boolean isSmsFallbackOn() {
		return !"N".equals(smsFallback);
	}
}
