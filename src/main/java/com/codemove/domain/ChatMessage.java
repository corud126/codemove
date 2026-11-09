package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 채팅 메시지 (TBL_CHAT_MESSAGE) */
@Getter
@Setter
public class ChatMessage {

	/** SENDER_TYPE: 01 고객 · 02 AI(챗봇) · 03 관리자 */
	public static final String CUSTOMER = "01";
	public static final String BOT = "02";
	public static final String ADMIN = "03";
	/** MESSAGE_TYPE: 01 텍스트 · 02 파일 */
	public static final String TEXT = "01";
	public static final String FILE = "02";

	private Long messageId;
	private Long roomId;
	private Long adminId;
	/** 챗봇 답변의 주제 — 대화를 다시 열 때 답변 버튼을 다시 그린다 */
	private Long topicId;
	private String senderType;
	private String messageType;
	/** 파일만 보낸 메시지는 NULL */
	private String content;
	/** uploads/chat 아래 저장 경로 */
	private String attachmentPath;
	/** 원본 파일명 */
	private String attachmentName;
	/** 받는 쪽이 읽었는지 (고객 메시지 → 관리자, 관리자 메시지 → 고객) */
	private String isRead;
	private LocalDateTime sentAt;

	/* 화면용 조회 값 */
	private String adminName;
	private String topicTitle;

	public boolean isFile() {
		return FILE.equals(messageType) && attachmentPath != null;
	}

	/** 사진이면 말풍선에 그대로 보여 준다 */
	public boolean isImage() {
		return isFile() && attachmentName != null
				&& attachmentName.toLowerCase().matches(".*\\.(jpe?g|png|gif|webp|bmp|heic)$");
	}
}
