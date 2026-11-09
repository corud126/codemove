package com.codemove.common.sms;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 개발용: 문자를 보내지 않고 서버 로그에 남긴다 (인증번호는 콘솔에서 확인) */
@Slf4j
@Component
public class LogSmsSender implements SmsSender {

	@Override
	public void send(String phone, String message) {
		log.info("[SMS] {} ← {}", phone, message);
	}
}
