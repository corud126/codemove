package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 채팅방 (TBL_CHAT_ROOM). 회원당 진행 중인 방은 1개, 종료 후 다시 문의하면 새 방 */
@Getter
@Setter
public class ChatRoom {

	/** CHAT_STATUS: 01 챗봇 응대 · 02 대기 중 · 03 상담 중 · 04 종료 */
	public static final String BOT = "01";
	public static final String WAITING = "02";
	public static final String ACTIVE = "03";
	public static final String ENDED = "04";

	private Long roomId;
	private Long memberId;
	/** 담당 관리자 */
	private Long adminId;
	private String status;
	private LocalDateTime startedAt;
	/** 상담원 연결 요청 시각 (대기 순서 · 지연 안내 기준) */
	private LocalDateTime waitingAt;
	private LocalDateTime endedAt;

	/* 상담함 · 채팅방 화면용 조회 값 (TBL_MEMBER · TBL_ADMIN · TBL_CHAT_MESSAGE) */
	private String memberName;
	private String phone;
	private String adminName;
	private String lastContent;
	private String lastAttachmentName;
	private LocalDateTime lastSentAt;
	/** 관리자가 안 읽은 고객 메시지 수 */
	private int unreadCount;

	public boolean isEnded() {
		return ENDED.equals(status);
	}
}
