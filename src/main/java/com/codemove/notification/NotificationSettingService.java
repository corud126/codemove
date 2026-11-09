package com.codemove.notification;

import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.NotificationSetting;

import lombok.RequiredArgsConstructor;

/**
 * 알림 설정 (TBL_NOTIFICATION_SETTING 한 줄) · 관리자 알림 받기 (TBL_ADMIN.PUSH_ENABLED).
 * 설정을 저장한 적이 없으면 기본값(자동 발송 켬 · 야간 보류 22:00~08:00 · 문자 대체 켬)을 쓰고, 처음 저장할 때 행을 만든다.
 * 고객 발송 설정은 알림톡·문자에만 적용된다 (ERD Q22). 관리자 알림 받기를 끄면 휴대폰 푸시만 끊기고 알림 패널에는 그대로 쌓인다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

	static final String DEFAULT_QUIET_START = "22:00";
	static final String DEFAULT_QUIET_END = "08:00";

	private final NotificationMapper mapper;

	public NotificationSetting get() {
		NotificationSetting s = mapper.selectSetting();
		return s != null ? s : defaults();
	}

	/** 알림 설정 탭 표시값 */
	public NotificationViews.Settings view(Long adminId) {
		NotificationSetting s = get();
		return new NotificationViews.Settings(isPushOn(adminId), s.isActiveOn(), s.isQuietOn(),
				s.getQuietStart() != null ? s.getQuietStart() : DEFAULT_QUIET_START,
				s.getQuietEnd() != null ? s.getQuietEnd() : DEFAULT_QUIET_END, s.isSmsFallbackOn());
	}

	/** 고객 발송 설정 저장. 시각은 "HH:mm" (10분 단위가 아니어도 저장은 한다) */
	@Transactional
	public NotificationSetting save(boolean active, boolean quiet, String quietStart, String quietEnd, boolean smsFallback,
			Long adminId) {
		if (!isTime(quietStart) || !isTime(quietEnd)) {
			throw new IllegalArgumentException("보류 시각을 다시 확인해 주세요.");
		}
		if (quiet && quietStart.equals(quietEnd)) {
			throw new IllegalArgumentException("보류 시작과 종료 시각이 같아요.");
		}
		NotificationSetting s = get();
		s.setIsActive(active ? "Y" : "N");
		s.setQuietEnabled(quiet ? "Y" : "N");
		s.setQuietStart(quietStart);
		s.setQuietEnd(quietEnd);
		s.setSmsFallback(smsFallback ? "Y" : "N");
		s.setAdminId(adminId);
		if (s.getSettingId() == null) {
			mapper.insertSetting(s);
		} else {
			mapper.updateSetting(s);
		}
		return s;
	}

	public boolean isPushOn(Long adminId) {
		return adminId != null && !"N".equals(mapper.selectPushEnabled(adminId));
	}

	/** 관리자 본인의 알림 받기 (휴대폰 푸시만) */
	@Transactional
	public void savePush(Long adminId, boolean on) {
		if (mapper.updatePushEnabled(adminId, on ? "Y" : "N") == 0) {
			throw new IllegalArgumentException("관리자 정보를 찾을 수 없어요.");
		}
	}

	/** 지금이 야간 발송 보류 시간인지 (시작 ≤ 지금 < 종료, 자정을 넘는 구간 포함) */
	public boolean isQuietNow(NotificationSetting s, LocalTime now) {
		if (!s.isQuietOn() || !isTime(s.getQuietStart()) || !isTime(s.getQuietEnd())) {
			return false;
		}
		LocalTime start = LocalTime.parse(s.getQuietStart());
		LocalTime end = LocalTime.parse(s.getQuietEnd());
		if (start.equals(end)) {
			return false;
		}
		return start.isBefore(end)
				? !now.isBefore(start) && now.isBefore(end)
				: !now.isBefore(start) || now.isBefore(end);
	}

	static boolean isTime(String s) {
		return s != null && s.matches("([01]\\d|2[0-3]):[0-5]\\d");
	}

	private static NotificationSetting defaults() {
		NotificationSetting s = new NotificationSetting();
		s.setIsActive("Y");
		s.setQuietEnabled("Y");
		s.setQuietStart(DEFAULT_QUIET_START);
		s.setQuietEnd(DEFAULT_QUIET_END);
		s.setSmsFallback("Y");
		return s;
	}
}
