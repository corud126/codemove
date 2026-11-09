package com.codemove.chat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.ChatbotAction;
import com.codemove.domain.ChatbotTopic;
import com.codemove.domain.Lorebook;
import com.codemove.domain.QuickMenu;

import lombok.RequiredArgsConstructor;

/** 관리자: 챗봇 답변 관리(주제 · 로어북 · 답변 버튼) · 채팅 시작 화면(퀵메뉴) */
@Service
@RequiredArgsConstructor
public class ChatbotAdminService {

	private final ChatbotMapper chatbotMapper;

	/** 퀵메뉴 저장 입력: menuId 가 기존 메뉴가 아니면 새로 만든다 */
	public record MenuForm(Long menuId, Long topicId, String label, boolean active) {
	}

	/** 주제 저장 입력 (코드 값) */
	public record TopicForm(Long topicId, String title, String intro, String topicType, boolean active,
			List<Lorebook> lorebooks, List<ChatbotAction> actions) {
	}

	/** 전체 주제 + 로어북 · 답변 버튼 (추가한 순서) */
	public List<ChatbotTopic> topics() {
		Map<Long, List<Lorebook>> lorebooks = chatbotMapper.selectLorebooks().stream()
				.collect(Collectors.groupingBy(Lorebook::getTopicId));
		Map<Long, List<ChatbotAction>> actions = chatbotMapper.selectActions().stream()
				.collect(Collectors.groupingBy(ChatbotAction::getTopicId));
		List<ChatbotTopic> topics = chatbotMapper.selectTopics();
		for (ChatbotTopic t : topics) {
			t.setLorebooks(lorebooks.getOrDefault(t.getTopicId(), List.of()));
			t.setActions(actions.getOrDefault(t.getTopicId(), List.of()));
		}
		return topics;
	}

	public ChatbotTopic topic(Long topicId) {
		ChatbotTopic t = chatbotMapper.selectTopic(topicId);
		if (t != null) {
			t.setLorebooks(chatbotMapper.selectLorebooksOfTopic(topicId));
			t.setActions(chatbotMapper.selectActionsOfTopic(topicId));
		}
		return t;
	}

	/** 주제 추가 · 수정. 로어북 · 답변 버튼은 통째로 바꾼다(목록 순서 = SORT_ORDER) */
	@Transactional
	public ChatbotTopic saveTopic(TopicForm form, Long adminId) {
		String title = trim(form.title());
		String intro = trim(form.intro());
		if (title.isEmpty() || title.length() > 50) {
			throw new IllegalArgumentException("고객이 누르는 버튼을 다시 확인해 주세요.");
		}
		if (intro.length() > 200) {
			throw new IllegalArgumentException("챗봇이 처음 하는 말은 200자까지 입력할 수 있어요.");
		}
		if (!List.of(ChatbotTopic.GUIDE, ChatbotTopic.RESERVATION, ChatbotTopic.QUOTE).contains(form.topicType())) {
			throw new IllegalArgumentException("답변 방식을 골라 주세요.");
		}
		ChatbotTopic topic = form.topicId() != null ? chatbotMapper.selectTopic(form.topicId()) : new ChatbotTopic();
		if (topic == null) {
			throw new IllegalArgumentException("이미 삭제된 주제입니다.");
		}
		topic.setTitle(title);
		topic.setIntro(intro.isEmpty() ? null : intro);
		topic.setTopicType(form.topicType());
		topic.setIsActive(form.active() ? "Y" : "N");
		topic.setAdminId(adminId);
		if (topic.getTopicId() == null) {
			chatbotMapper.insertTopic(topic);
		} else {
			chatbotMapper.updateTopic(topic);
			chatbotMapper.deleteLorebooksOfTopic(topic.getTopicId());
			chatbotMapper.deleteActionsOfTopic(topic.getTopicId());
		}
		// 로어북은 안내형만
		int order = 1;
		if (ChatbotTopic.GUIDE.equals(form.topicType()) && form.lorebooks() != null) {
			for (Lorebook lb : form.lorebooks()) {
				String keywords = trim(lb.getKeywords());
				String content = trim(lb.getContent());
				if (keywords.isEmpty() && content.isEmpty()) {
					continue;
				}
				if (keywords.isEmpty() || content.isEmpty() || keywords.length() > 200 || content.length() > 1000) {
					throw new IllegalArgumentException("AI에게 알려 줄 정보의 '이럴 때'와 '이렇게 답해요'를 모두 입력해 주세요.");
				}
				lb.setTopicId(topic.getTopicId());
				lb.setKeywords(keywords);
				lb.setContent(content);
				lb.setSortOrder(order++);
				chatbotMapper.insertLorebook(lb);
			}
		}
		order = 1;
		if (form.actions() != null) {
			for (ChatbotAction a : form.actions()) {
				String label = trim(a.getLabel());
				if (label.isEmpty()) {
					continue;
				}
				String value = trim(a.getActionValue());
				if (label.length() > 20 || value.length() > 200) {
					throw new IllegalArgumentException("답변 아래 버튼을 다시 확인해 주세요.");
				}
				if (!List.of(ChatbotAction.LINK, ChatbotAction.ASK, ChatbotAction.AGENT).contains(a.getActionType())) {
					throw new IllegalArgumentException("버튼을 누르면 할 동작을 골라 주세요.");
				}
				if (ChatbotAction.LINK.equals(a.getActionType()) && !ChatScreens.isSafeUrl(value)) {
					throw new IllegalArgumentException("이동할 화면을 골라 주세요.");
				}
				a.setTopicId(topic.getTopicId());
				a.setLabel(label);
				a.setActionValue(ChatbotAction.AGENT.equals(a.getActionType()) || value.isEmpty() ? null : value);
				a.setSortOrder(order++);
				chatbotMapper.insertAction(a);
			}
		}
		return topic(topic.getTopicId());
	}

	/** 사용 / 사용 안 함 (바로 적용) */
	@Transactional
	public void setActive(Long topicId, boolean active, Long adminId) {
		chatbotMapper.updateTopicActive(topicId, active ? "Y" : "N", adminId);
	}

	/** 주제 삭제: 연결된 퀵메뉴 · 로어북 · 버튼도 지우고, 지난 답변의 주제 연결은 끊는다 */
	@Transactional
	public void deleteTopic(Long topicId) {
		chatbotMapper.clearMessageTopic(topicId);
		chatbotMapper.deleteQuickMenusOfTopic(topicId);
		chatbotMapper.deleteLorebooksOfTopic(topicId);
		chatbotMapper.deleteActionsOfTopic(topicId);
		chatbotMapper.deleteTopic(topicId);
	}

	/* ---------- 퀵메뉴 ---------- */

	public List<QuickMenu> quickMenus() {
		return chatbotMapper.selectQuickMenus();
	}

	/** 고객 채팅 첫 화면에 보일 퀵메뉴 (노출 + 주제 사용 중) */
	public List<QuickMenu> visibleQuickMenus() {
		return chatbotMapper.selectQuickMenus().stream()
				.filter(m -> "Y".equals(m.getIsActive()) && "Y".equals(m.getTopicActive())).toList();
	}

	/** 퀵메뉴 통째로 저장 (배열 순서 = SORT_ORDER, 최대 5개, 주제 중복 불가) */
	@Transactional
	public List<QuickMenu> saveQuickMenus(List<MenuForm> forms, Long adminId) {
		List<MenuForm> list = forms == null ? List.of() : forms;
		if (list.size() > QuickMenu.MAX) {
			throw new IllegalArgumentException("퀵메뉴는 " + QuickMenu.MAX + "개까지 넣을 수 있어요.");
		}
		Set<Long> topicIds = new HashSet<>();
		for (MenuForm f : list) {
			String label = trim(f.label());
			if (label.isEmpty() || label.length() > 50) {
				throw new IllegalArgumentException("버튼 글자를 다시 확인해 주세요.");
			}
			if (f.topicId() == null || chatbotMapper.selectTopic(f.topicId()) == null || !topicIds.add(f.topicId())) {
				throw new IllegalArgumentException("연결된 주제를 다시 골라 주세요.");
			}
		}
		Map<Long, QuickMenu> existing = chatbotMapper.selectQuickMenus().stream()
				.collect(Collectors.toMap(QuickMenu::getMenuId, m -> m));
		Set<Long> kept = new HashSet<>();
		int order = 1;
		for (MenuForm f : list) {
			QuickMenu m = f.menuId() != null ? existing.get(f.menuId()) : null;
			boolean isNew = m == null;
			if (isNew) {
				m = new QuickMenu();
			}
			m.setTopicId(f.topicId());
			m.setMenuName(trim(f.label()));
			m.setSortOrder(order++);
			m.setIsActive(f.active() ? "Y" : "N");
			m.setAdminId(adminId);
			if (isNew) {
				chatbotMapper.insertQuickMenu(m);
			} else {
				chatbotMapper.updateQuickMenu(m);
			}
			kept.add(m.getMenuId());
		}
		for (Long id : existing.keySet()) {
			if (!kept.contains(id)) {
				chatbotMapper.deleteQuickMenu(id);
			}
		}
		return chatbotMapper.selectQuickMenus();
	}

	private static String trim(String s) {
		return s == null ? "" : s.strip();
	}
}
