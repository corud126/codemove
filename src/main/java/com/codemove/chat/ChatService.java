package com.codemove.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.domain.ChatMessage;
import com.codemove.domain.ChatRoom;
import com.codemove.domain.ChatSetting;
import com.codemove.domain.ChatbotAction;
import com.codemove.member.LoginMember;

/**
 * 채팅 상담 흐름.
 * 고객: 첫 메시지를 보낼 때 방을 만든다(챗봇 사용 중이면 01 챗봇 응대, 꺼져 있으면 02 상담 대기).
 *       챗봇 응대 중에는 ChatbotEngine 이 답하고, [상담사 연결 요청]으로 02 대기가 된다.
 * 관리자: 답장하면 03 상담 중(담당자 = 처음 답한 관리자), [상담 종료]로 04. 종료 후 고객이 다시 쓰면 새 방.
 * 실시간 표시는 화면에서 짧은 주기로 다시 불러오는 방식(폴링)이다.
 * 지연 안내 · 무응답 자동 종료는 별도 스케줄러 없이 화면을 불러올 때 함께 처리한다.
 */
@Service
public class ChatService {

	public static final int TEXT_MAX = 1000;
	/** 관리자 채팅방에 한 번에 보여 줄 최근 메시지 수 */
	static final int ADMIN_HISTORY = 200;

	static final String AGENT_REQUEST = "상담사 연결 요청";
	static final String ACCEPTED = "상담사 연결 요청을 접수했어요.\n운영시간에는 순서대로 상담이 시작되며, 답변이 오면 알림으로 알려드릴게요.";
	static final String AWAY_DEFAULT = "지금은 상담 시간이 아니에요.\n남겨 주신 문의는 상담 시간에 순서대로 답변드릴게요.";
	static final String ALREADY_WAITING = "이미 상담사 연결을 요청했어요.\n순서대로 연결해 드릴게요.";
	static final String ALREADY_ACTIVE = "상담사와 연결되어 있어요.\n궁금한 내용을 메시지로 남겨 주세요.";

	private final ChatMapper chatMapper;
	private final ChatbotMapper chatbotMapper;
	private final ChatSettingService settingService;
	private final ChatbotEngine engine;
	private final ChatFileStore fileStore;
	/** 대기 후 지연 안내를 보낼 때까지(분) */
	private final int delayMinutes;
	/** 챗봇 응대 · 상담 중인 방을 자동 종료할 무응답 시간(분) */
	private final int idleMinutes;

	public ChatService(ChatMapper chatMapper, ChatbotMapper chatbotMapper, ChatSettingService settingService,
			ChatbotEngine engine, ChatFileStore fileStore,
			@Value("${codemove.chat.delay-minutes:10}") int delayMinutes,
			@Value("${codemove.chat.idle-end-minutes:30}") int idleMinutes) {
		this.chatMapper = chatMapper;
		this.chatbotMapper = chatbotMapper;
		this.settingService = settingService;
		this.engine = engine;
		this.fileStore = fileStore;
		this.delayMinutes = delayMinutes;
		this.idleMinutes = idleMinutes;
	}

	/** 사용자 채팅 화면에 보낼 상태: 가장 최근 방의 상태와 메시지 */
	public record MemberChat(String status, List<Map<String, Object>> messages) {
	}

	/** 관리자 채팅방 화면 데이터 */
	public record AdminRoom(ChatRoom room, List<AdminChatItem> items, MemberQuote quote, MemberReservation reservation) {
	}

	/* ==================== 고객 ==================== */

	/** 채팅 화면 첫 진입: 무응답 방 정리 → 가장 최근 방의 대화 (종료된 방이어도 보여 주고, 새로 쓰면 새 방) */
	@Transactional
	public MemberChat open(Long memberId) {
		chatMapper.endIdleRooms(idleMinutes, memberId);
		sendDelayNotices(memberId);
		ChatRoom room = chatMapper.selectLatestRoom(memberId);
		chatMapper.markRead(memberId, ChatMessage.ADMIN);
		if (room == null) {
			return new MemberChat(null, List.of());
		}
		return new MemberChat(room.getStatus(), toUserView(chatMapper.selectRoomMessages(room.getRoomId()), List.of()));
	}

	/** 폴링: afterId 뒤에 온 메시지 (상담사 답장 · 지연 안내) */
	@Transactional
	public MemberChat poll(Long memberId, long afterId) {
		sendDelayNotices(memberId);
		List<ChatMessage> messages = chatMapper.selectMemberMessagesAfter(memberId, afterId);
		chatMapper.markRead(memberId, ChatMessage.ADMIN);
		ChatRoom room = chatMapper.selectLatestRoom(memberId);
		return new MemberChat(room != null ? room.getStatus() : null, toUserView(messages, List.of()));
	}

	/**
	 * 고객 메시지 (직접 입력 · 퀵메뉴 · 답변 버튼의 이어서 질문).
	 * @param topicId 퀵메뉴 · 주제 버튼이면 그 주제
	 * @return 새로 생긴 메시지 (고객 메시지 + 챗봇 답변 또는 안내)
	 */
	@Transactional
	public List<Map<String, Object>> ask(LoginMember member, String rawText, Long topicId) {
		String text = cleanText(rawText);
		if (text.isEmpty()) {
			throw new IllegalArgumentException("메시지를 입력해 주세요.");
		}
		ChatSetting setting = settingService.get();
		Opened opened = openRoom(member.memberId(), setting);
		ChatRoom room = opened.room();
		List<ChatMessage> added = new ArrayList<>();
		added.add(insertCustomer(room, text, null, null));
		List<ChatbotEngine.Button> buttons = List.of();
		if (ChatRoom.BOT.equals(room.getStatus()) && !setting.isBotOn()) {
			// 챗봇이 꺼졌으면 진행 중인 챗봇 방도 바로 상담 대기로
			chatMapper.updateWaiting(room.getRoomId());
			room.setStatus(ChatRoom.WAITING);
			added.add(insertBot(room, waitingNotice(setting), null));
			// TODO 알림 단계: 관리자에게 '채팅 상담 대기'(NTF_DETAIL 13) 알림
		} else if (ChatRoom.BOT.equals(room.getStatus())) {
			ChatbotEngine.Reply reply = engine.answer(
					new ChatbotEngine.Question(member.memberId(), member.name(), text, topicId));
			added.add(insertBot(room, reply.text(), reply.topicId()));
			buttons = reply.buttons();
		} else if (opened.created()) {
			added.add(insertBot(room, waitingNotice(setting), null));
			// TODO 알림 단계: 관리자에게 '채팅 상담 대기'(NTF_DETAIL 13) 알림
		}
		return toUserView(added, buttons);
	}

	/** [상담사 연결 요청]: 챗봇 응대 → 상담 대기 */
	@Transactional
	public List<Map<String, Object>> requestAgent(LoginMember member) {
		ChatSetting setting = settingService.get();
		Opened opened = openRoom(member.memberId(), setting);
		ChatRoom room = opened.room();
		List<ChatMessage> added = new ArrayList<>();
		added.add(insertCustomer(room, AGENT_REQUEST, null, null));
		String notice;
		if (ChatRoom.BOT.equals(room.getStatus())) {
			if (setting.isConsultOn()) {
				chatMapper.updateWaiting(room.getRoomId());
				notice = waitingNotice(setting);
				// TODO 알림 단계: 관리자에게 '채팅 상담 대기'(NTF_DETAIL 13) 알림
			} else {
				// 상담 운영 꺼짐(휴무 등): 연결 대신 부재 안내
				notice = awayText(setting);
			}
		} else if (ChatRoom.WAITING.equals(room.getStatus())) {
			notice = opened.created() ? waitingNotice(setting) : ALREADY_WAITING;
		} else {
			notice = ALREADY_ACTIVE;
		}
		added.add(insertBot(room, notice, null));
		return toUserView(added, List.of());
	}

	/** 고객 파일 첨부 (+ 함께 쓴 글) */
	@Transactional
	public List<Map<String, Object>> sendFile(LoginMember member, MultipartFile file, String rawText) {
		String text = cleanText(rawText);
		ChatSetting setting = settingService.get();
		ChatRoom room = openRoom(member.memberId(), setting).room();
		String path = fileStore.save(room.getRoomId(), file);
		String name = ChatFileStore.displayName(file.getOriginalFilename());
		List<ChatMessage> added = new ArrayList<>();
		added.add(insertCustomer(room, text.isEmpty() ? null : text, path, name));
		List<ChatbotEngine.Button> buttons = List.of();
		if (ChatRoom.BOT.equals(room.getStatus())) {
			added.add(insertBot(room, name + " 파일을 받았어요.\n상담사가 확인할 수 있도록 상담사 연결을 요청해 주세요."
					+ "\n개인정보가 포함된 문서는 필요한 부분만 남기고 가려서 보내주세요.", null));
			buttons = List.of(new ChatbotEngine.Button(AGENT_REQUEST, "agent", null));
		}
		return toUserView(added, buttons);
	}

	/** 첨부 파일 (본인 방의 메시지만) */
	public ChatMessage memberFile(Long memberId, Long messageId) {
		Long owner = chatMapper.selectMessageMemberId(messageId);
		if (owner == null || !owner.equals(memberId)) {
			return null;
		}
		ChatMessage m = chatMapper.selectMessage(messageId);
		return m != null && m.isFile() ? m : null;
	}

	/* ==================== 관리자 ==================== */

	/** 상담함: 무응답 방 정리 · 지연 안내 후 회원별 최근 방 */
	@Transactional
	public List<ChatRoom> rooms() {
		chatMapper.endIdleRooms(idleMinutes, null);
		sendDelayNotices(null);
		return chatMapper.selectLatestRooms();
	}

	/** 상담함 검색 (이름 · 휴대폰 · 대화 내용) → MEMBER_ID */
	public List<Long> search(String query) {
		String q = query == null ? "" : query.strip();
		if (q.isEmpty()) {
			return List.of();
		}
		String digits = q.replaceAll("\\D", "");
		return chatMapper.searchMemberIds(q, digits.length() >= 3 ? digits : null);
	}

	/** 채팅방 (MEMBER_ID). 열면 고객 메시지를 읽음 처리한다. 채팅한 적 없는 회원이면 null */
	@Transactional
	public AdminRoom adminRoom(Long memberId) {
		ChatRoom room = chatMapper.selectLatestRoom(memberId);
		if (room == null) {
			return null;
		}
		chatMapper.markRead(memberId, ChatMessage.CUSTOMER);
		List<ChatMessage> messages = new ArrayList<>(chatMapper.selectRecentMemberMessages(memberId, ADMIN_HISTORY));
		Collections.reverse(messages);
		List<AdminChatItem> items = timeline(room, chatMapper.selectRoomsOfMember(memberId), messages);
		return new AdminRoom(room, items, chatMapper.selectMemberQuote(memberId), chatMapper.selectMemberReservation(memberId));
	}

	/** 관리자 답장 (글 또는 파일). 대기 · 챗봇 응대 방은 상담 중이 되고 담당자가 정해진다 */
	@Transactional
	public void adminSend(Long adminId, Long memberId, String rawText, MultipartFile file) {
		ChatRoom room = chatMapper.selectLatestRoom(memberId);
		if (room == null || room.isEnded()) {
			throw new IllegalStateException("종료된 상담입니다.");
		}
		String text = cleanText(rawText);
		ChatMessage m = new ChatMessage();
		m.setRoomId(room.getRoomId());
		m.setAdminId(adminId);
		m.setSenderType(ChatMessage.ADMIN);
		m.setIsRead("N");
		if (file != null && !file.isEmpty()) {
			m.setMessageType(ChatMessage.FILE);
			m.setAttachmentPath(fileStore.save(room.getRoomId(), file));
			m.setAttachmentName(ChatFileStore.displayName(file.getOriginalFilename()));
			m.setContent(text.isEmpty() ? null : text);
		} else {
			if (text.isEmpty()) {
				throw new IllegalArgumentException("메시지를 입력해 주세요.");
			}
			m.setMessageType(ChatMessage.TEXT);
			m.setContent(text);
		}
		chatMapper.updateActive(room.getRoomId(), adminId);
		chatMapper.insertMessage(m);
		// TODO 알림 단계: 고객에게 '상담 답변 도착'(NTF_DETAIL 15) 알림
	}

	/** 상담 종료 */
	@Transactional
	public void end(Long adminId, Long memberId) {
		ChatRoom room = chatMapper.selectLatestRoom(memberId);
		if (room != null) {
			chatMapper.updateEnded(room.getRoomId(), adminId);
		}
	}

	/** 첨부 파일 (관리자는 모든 방) */
	public ChatMessage adminFile(Long messageId) {
		ChatMessage m = chatMapper.selectMessage(messageId);
		return m != null && m.isFile() ? m : null;
	}

	public java.nio.file.Path filePath(ChatMessage m) {
		return fileStore.resolve(m.getAttachmentPath());
	}

	/* ==================== 내부 ==================== */

	private record Opened(ChatRoom room, boolean created) {
	}

	/** 진행 중인 방, 없거나 종료됐으면 새 방 (회원당 진행 중인 방은 1개) */
	private Opened openRoom(Long memberId, ChatSetting setting) {
		ChatRoom room = chatMapper.selectLatestRoom(memberId);
		if (room != null && !room.isEnded()) {
			return new Opened(room, false);
		}
		ChatRoom created = new ChatRoom();
		created.setMemberId(memberId);
		created.setStatus(setting.isBotOn() ? ChatRoom.BOT : ChatRoom.WAITING);
		chatMapper.insertRoom(created);
		return new Opened(created, true);
	}

	/** 지연 안내: 오래 기다린 대기 방에 DELAY_MESSAGE 를 한 번 보낸다 (memberId 가 null 이면 모든 방) */
	private void sendDelayNotices(Long memberId) {
		ChatSetting setting = settingService.get();
		if (setting.getDelayMessage() == null || setting.getDelayMessage().isBlank()) {
			return;
		}
		for (ChatRoom room : chatMapper.selectOverdueWaitingRooms(delayMinutes, setting.getDelayMessage(), memberId)) {
			insertBot(room, setting.getDelayMessage(), null);
		}
	}

	/** 대기 접수 안내: 운영 시간이면 접수, 아니면 부재 안내 */
	private String waitingNotice(ChatSetting setting) {
		return settingService.isConsultOpen(setting, LocalDateTime.now()) ? ACCEPTED : awayText(setting);
	}

	private static String awayText(ChatSetting setting) {
		String away = setting.getAwayMessage();
		return away != null && !away.isBlank() ? away : AWAY_DEFAULT;
	}

	private ChatMessage insertCustomer(ChatRoom room, String text, String path, String name) {
		ChatMessage m = new ChatMessage();
		m.setRoomId(room.getRoomId());
		m.setSenderType(ChatMessage.CUSTOMER);
		m.setMessageType(path != null ? ChatMessage.FILE : ChatMessage.TEXT);
		m.setContent(text);
		m.setAttachmentPath(path);
		m.setAttachmentName(name);
		m.setIsRead("N");
		chatMapper.insertMessage(m);
		m.setSentAt(LocalDateTime.now());
		return m;
	}

	/** 챗봇 메시지는 고객이 화면에서 바로 보므로 읽음으로 저장 */
	private ChatMessage insertBot(ChatRoom room, String text, Long topicId) {
		ChatMessage m = new ChatMessage();
		m.setRoomId(room.getRoomId());
		m.setTopicId(topicId);
		m.setSenderType(ChatMessage.BOT);
		m.setMessageType(ChatMessage.TEXT);
		m.setContent(text.length() > TEXT_MAX ? text.substring(0, TEXT_MAX) : text);
		m.setIsRead("Y");
		chatMapper.insertMessage(m);
		m.setSentAt(LocalDateTime.now());
		return m;
	}

	private static String cleanText(String raw) {
		String text = raw == null ? "" : raw.strip();
		if (text.length() > TEXT_MAX) {
			throw new IllegalArgumentException("메시지는 " + TEXT_MAX + "자까지 보낼 수 있어요.");
		}
		return text;
	}

	/** 주제별 답변 버튼 */
	private Map<Long, List<ChatbotAction>> actionsByTopic() {
		return chatbotMapper.selectActions().stream()
				.collect(Collectors.groupingBy(ChatbotAction::getTopicId, LinkedHashMap::new, Collectors.toList()));
	}

	/**
	 * 사용자 채팅 화면(chat.js)용 메시지:
	 * {id, role: user|bot, text, time, attachment: {name, url, image}, actions: [{label, href|prompt|agent|topic}]}
	 * 챗봇 답변의 버튼은 주제(TOPIC_ID)에서 다시 만들고, extra 는 마지막 챗봇 메시지에만 붙인다.
	 */
	List<Map<String, Object>> toUserView(List<ChatMessage> messages, List<ChatbotEngine.Button> extra) {
		Map<Long, List<ChatbotAction>> actions = messages.stream().anyMatch(m -> m.getTopicId() != null)
				? actionsByTopic() : Map.of();
		ChatMessage lastBot = messages.stream().filter(m -> ChatMessage.BOT.equals(m.getSenderType()))
				.reduce((a, b) -> b).orElse(null);
		List<Map<String, Object>> list = new ArrayList<>();
		for (ChatMessage m : messages) {
			Map<String, Object> v = new LinkedHashMap<>();
			v.put("id", m.getMessageId());
			v.put("role", ChatMessage.CUSTOMER.equals(m.getSenderType()) ? "user" : "bot");
			v.put("text", m.getContent() != null ? m.getContent() : (m.isFile() ? "파일을 첨부했어요." : ""));
			v.put("time", ChatTimes.hhmm(m.getSentAt()));
			if (m.isFile()) {
				v.put("attachment", Map.of("name", m.getAttachmentName(), "url", "/chat/files/" + m.getMessageId(),
						"image", m.isImage()));
			}
			List<Map<String, Object>> buttons = new ArrayList<>();
			List<ChatbotAction> topicActions = m.getTopicId() != null ? actions.getOrDefault(m.getTopicId(), List.of()) : List.of();
			for (ChatbotAction a : topicActions) {
				buttons.add(button(a.getLabel(), switch (a.getActionType()) {
					case ChatbotAction.LINK -> "link";
					case ChatbotAction.ASK -> "ask";
					default -> "agent";
				}, a.getActionValue()));
			}
			if (m == lastBot) {
				extra.forEach(b -> buttons.add(button(b.label(), b.type(), b.value())));
			}
			buttons.removeIf(b -> b == null);
			if (!buttons.isEmpty()) {
				v.put("actions", buttons);
			}
			list.add(v);
		}
		return list;
	}

	private static Map<String, Object> button(String label, String type, String value) {
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("label", label);
		switch (type) {
			case "link" -> {
				if (!ChatScreens.isSafeUrl(value)) {
					return null;
				}
				b.put("href", value);
			}
			case "ask" -> b.put("prompt", value != null && !value.isBlank() ? value : label);
			case "topic" -> b.put("topic", value);
			default -> b.put("agent", true);
		}
		return b;
	}

	/** 관리자 채팅방 줄: 메시지 + 대기 시작 · 종료 안내 줄 + 날짜 줄, 고객 말 묶음 · 답변 대기 표시 */
	private List<AdminChatItem> timeline(ChatRoom current, List<ChatRoom> rooms, List<ChatMessage> messages) {
		Map<Long, List<ChatbotAction>> actions = actionsByTopic();
		LocalDateTime from = messages.isEmpty() ? current.getStartedAt() : messages.get(0).getSentAt();
		record Event(LocalDateTime at, int order, AdminChatItem item) {
		}
		List<Event> events = new ArrayList<>();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			AdminChatItem item = new AdminChatItem();
			item.setKind(switch (m.getSenderType()) {
				case ChatMessage.CUSTOMER -> "customer";
				case ChatMessage.BOT -> "ai";
				default -> "admin";
			});
			item.setMessage(m);
			item.setText(m.getContent());
			item.setTime(ChatTimes.hhmm(m.getSentAt()));
			if (m.getTopicId() != null) {
				item.setActions(actions.getOrDefault(m.getTopicId(), List.of()));
			}
			if ("customer".equals(item.getKind()) && ChatRoom.WAITING.equals(current.getStatus())
					&& m.getRoomId().equals(current.getRoomId()) && current.getWaitingAt() != null
					&& !m.getSentAt().isBefore(current.getWaitingAt().withNano(0))) {
				item.setWaiting(true);
			}
			events.add(new Event(m.getSentAt(), i, item));
		}
		for (ChatRoom r : rooms) {
			if (r.getWaitingAt() != null && !r.getWaitingAt().isBefore(from)) {
				events.add(new Event(r.getWaitingAt(), Integer.MAX_VALUE - 1,
						AdminChatItem.line("system", "상담 대기 시작 · " + ChatTimes.hhmm(r.getWaitingAt()))));
			}
			if (r.getEndedAt() != null && !r.getEndedAt().isBefore(from)) {
				events.add(new Event(r.getEndedAt(), Integer.MAX_VALUE,
						AdminChatItem.line("system", "상담 종료 · " + ChatTimes.hhmm(r.getEndedAt()))));
			}
		}
		events.sort(Comparator.comparing(Event::at).thenComparingInt(Event::order));
		List<AdminChatItem> items = new ArrayList<>();
		LocalDate day = null;
		String prevKind = null;
		for (Event e : events) {
			LocalDate d = e.at().toLocalDate();
			if (!d.equals(day)) {
				items.add(AdminChatItem.line("day", ChatTimes.dayLabel(d)));
				day = d;
				prevKind = "day";
			}
			AdminChatItem item = e.item();
			item.setGroupStart("customer".equals(item.getKind()) && !"customer".equals(prevKind));
			items.add(item);
			prevKind = item.getKind();
		}
		return items;
	}
}
