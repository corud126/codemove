package com.codemove.chat;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.ChatbotAction;
import com.codemove.domain.ChatbotTopic;
import com.codemove.domain.Lorebook;
import com.codemove.domain.QuickMenu;

/** 챗봇 주제 · 로어북 · 답변 버튼 · 퀵메뉴 */
@Mapper
public interface ChatbotMapper {

	/** 전체 주제 (추가한 순서) */
	List<ChatbotTopic> selectTopics();

	ChatbotTopic selectTopic(Long topicId);

	/** 전체 로어북 (주제 · SORT_ORDER 순) */
	List<Lorebook> selectLorebooks();

	List<Lorebook> selectLorebooksOfTopic(Long topicId);

	/** 전체 답변 버튼 (주제 · SORT_ORDER 순) */
	List<ChatbotAction> selectActions();

	List<ChatbotAction> selectActionsOfTopic(Long topicId);

	/** TOPIC_ID 는 SEQ_CHATBOT_TOPIC */
	int insertTopic(ChatbotTopic topic);

	int updateTopic(ChatbotTopic topic);

	int updateTopicActive(@Param("topicId") Long topicId, @Param("isActive") String isActive,
			@Param("adminId") Long adminId);

	int deleteTopic(Long topicId);

	/** LOREBOOK_ID 는 SEQ_LOREBOOK */
	int insertLorebook(Lorebook lorebook);

	int deleteLorebooksOfTopic(Long topicId);

	/** ACTION_ID 는 SEQ_CHATBOT_ACTION */
	int insertAction(ChatbotAction action);

	int deleteActionsOfTopic(Long topicId);

	/** 주제를 지우기 전에 지난 챗봇 답변의 주제 연결을 끊는다 (대화 기록은 남김) */
	int clearMessageTopic(Long topicId);

	/* ---------- 퀵메뉴 ---------- */

	/** SORT_ORDER 순 + 연결 주제 사용 여부 */
	List<QuickMenu> selectQuickMenus();

	/** MENU_ID 는 SEQ_QUICK_MENU */
	int insertQuickMenu(QuickMenu menu);

	int updateQuickMenu(QuickMenu menu);

	int deleteQuickMenu(Long menuId);

	int deleteQuickMenusOfTopic(Long topicId);
}
