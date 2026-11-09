package com.codemove.chat;

import java.util.List;

import com.codemove.domain.ChatMessage;
import com.codemove.domain.ChatbotAction;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 채팅방(admin/chat/detail) 한 줄.
 * kind: day(날짜 줄) · system(대기 시작 · 종료 안내 줄) · customer · ai · admin
 */
@Getter
@Setter
public class AdminChatItem {

	private String kind;
	private String text;
	private String time;
	private ChatMessage message;
	/** 고객 말 묶음의 첫 말풍선 (프로필 원 + 이름) */
	private boolean groupStart;
	/** 답변을 기다리는 고객 메시지 (왼쪽으로 밀어 답장) */
	private boolean waiting;
	/** AI 답변 아래 고객에게 보였던 버튼 */
	private List<ChatbotAction> actions = List.of();

	static AdminChatItem line(String kind, String text) {
		AdminChatItem item = new AdminChatItem();
		item.setKind(kind);
		item.setText(text);
		return item;
	}
}
