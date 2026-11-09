package com.codemove.chat;

import java.util.List;

/**
 * 챗봇 답변 생성기.
 * 지금은 DB 시나리오(주제 · 로어북 · 답변 버튼)로 답하는 ScenarioChatbotEngine 하나다.
 * 외부 AI(Dialogflow · LLM)를 붙일 때는 이 인터페이스를 구현한 빈을 @Primary 로 등록하면 된다
 * (안내형 주제의 로어북 CONTENT 를 프롬프트 재료로 넘기는 방식).
 */
public interface ChatbotEngine {

	/**
	 * @return 답변. topicId 가 있으면 그 주제의 답변 버튼(TBL_CHATBOT_ACTION)을 함께 보여 준다
	 */
	Reply answer(Question question);

	/**
	 * @param topicId 퀵메뉴 · 주제 버튼을 눌렀으면 그 주제, 직접 입력이면 null
	 */
	record Question(Long memberId, String memberName, String text, Long topicId) {
	}

	/**
	 * @param topicId 답한 주제 (TBL_CHAT_MESSAGE.TOPIC_ID 로 저장)
	 * @param buttons 주제 버튼 외에 이번 답변에만 붙이는 버튼 (저장하지 않음)
	 */
	record Reply(String text, Long topicId, List<Button> buttons) {
	}

	/**
	 * @param type link(화면 이동, value=주소) · ask(질문 보내기, value=문구) · agent(상담원 연결) · topic(value=TOPIC_ID)
	 */
	record Button(String label, String type, String value) {
	}
}
