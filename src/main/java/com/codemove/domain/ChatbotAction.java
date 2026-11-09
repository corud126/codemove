package com.codemove.domain;

import lombok.Getter;
import lombok.Setter;

/** 챗봇 답변 아래 버튼 (TBL_CHATBOT_ACTION) */
@Getter
@Setter
public class ChatbotAction {

	/** ACTION_TYPE: 01 화면 이동 · 02 이어서 질문 · 03 상담원 연결 */
	public static final String LINK = "01";
	public static final String ASK = "02";
	public static final String AGENT = "03";

	private Long actionId;
	private Long topicId;
	private String label;
	private String actionType;
	/** 이동 주소 또는 질문 문구 (상담원 연결은 NULL) */
	private String actionValue;
	private Integer sortOrder;
}
