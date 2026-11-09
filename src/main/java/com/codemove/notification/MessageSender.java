package com.codemove.notification;

/**
 * 휴대폰 메시지 발송 (알림톡 · 관리자 앱 푸시). 실제 서비스 연동 시 이 인터페이스의 구현체를 추가한다.
 * 문자 대체 발송은 기존 SmsSender 를 쓴다.
 */
public interface MessageSender {

	/** 고객 알림톡. 실패하면 false (설정에 따라 문자로 대체) */
	boolean sendAlimtalk(String phone, String title, String content);

	/** 관리자 휴대폰 푸시 (알림 받기를 켠 관리자만) */
	void sendPush(String phone, String title, String content);
}
