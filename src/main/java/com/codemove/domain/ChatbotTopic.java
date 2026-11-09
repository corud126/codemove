package com.codemove.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 챗봇 주제 (TBL_CHATBOT_TOPIC) + 로어북 · 답변 버튼 */
@Getter
@Setter
public class ChatbotTopic {

	/** TOPIC_TYPE: 01 안내 · 02 예약 조회 · 03 견적 조회 */
	public static final String GUIDE = "01";
	public static final String RESERVATION = "02";
	public static final String QUOTE = "03";

	private Long topicId;
	private String title;
	private String intro;
	private String topicType;
	private String isActive;
	private LocalDateTime updatedAt;
	private Long adminId;

	/** 안내형만 사용 (SORT_ORDER 순) */
	private List<Lorebook> lorebooks = new ArrayList<>();
	/** 답변 아래 버튼 (SORT_ORDER 순) */
	private List<ChatbotAction> actions = new ArrayList<>();

	public boolean isActiveTopic() {
		return "Y".equals(isActive);
	}
}
