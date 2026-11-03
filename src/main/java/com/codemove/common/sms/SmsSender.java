package com.codemove.common.sms;

/** 문자 발송. 실제 문자 서비스 연동 시 이 인터페이스의 구현체를 추가한다 */
public interface SmsSender {

	void send(String phone, String message);
}
