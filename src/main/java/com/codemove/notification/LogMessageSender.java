package com.codemove.notification;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 개발용: 알림톡 · 푸시를 보내지 않고 서버 로그에 남긴다 (알림톡은 항상 성공으로 처리) */
@Slf4j
@Component
public class LogMessageSender implements MessageSender {

	@Override
	public boolean sendAlimtalk(String phone, String title, String content) {
		log.info("[알림톡] {} ← {} / {}", phone, title, content);
		return true;
	}

	@Override
	public void sendPush(String phone, String title, String content) {
		log.info("[푸시] {} ← {} / {}", phone, title, content);
	}
}
