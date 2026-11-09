package com.codemove.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 정기 작업.
 * - 이사 임박(D-3) 알림: 매일 (기본 오전 9시, codemove.notification.reminder-cron)
 * - 야간 보류 알림 발송: 보류 시간이 끝나면 (기본 10분마다 확인, codemove.notification.flush-cron)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

	private final NotificationService notificationService;

	@Scheduled(cron = "${codemove.notification.reminder-cron:0 0 9 * * *}")
	public void moveReminders() {
		try {
			int sent = notificationService.sendMoveReminders();
			if (sent > 0) {
				log.info("이사 임박 알림 {}건", sent);
			}
		} catch (RuntimeException e) {
			log.warn("이사 임박 알림 실패", e);
		}
	}

	@Scheduled(cron = "${codemove.notification.flush-cron:0 */10 * * * *}")
	public void flushHeld() {
		try {
			int sent = notificationService.flushHeld();
			if (sent > 0) {
				log.info("보류 알림 발송 {}건", sent);
			}
		} catch (RuntimeException e) {
			log.warn("보류 알림 발송 실패", e);
		}
	}
}
