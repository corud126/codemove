package com.codemove.web.admin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.admin.LoginAdmin;
import com.codemove.chat.ChatScreens;
import com.codemove.chat.ChatService;
import com.codemove.chat.ChatSettingService;
import com.codemove.chat.ChatTimes;
import com.codemove.chat.ChatbotAdminService;
import com.codemove.chat.SavedReplyService;
import com.codemove.common.code.CodeService;
import com.codemove.domain.ChatRoom;
import com.codemove.domain.ChatbotAction;
import com.codemove.domain.ChatbotTopic;
import com.codemove.domain.Code;
import com.codemove.domain.Lorebook;
import com.codemove.domain.QuickMenu;
import com.codemove.domain.SavedReply;
import com.codemove.web.user.ChatController;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 채팅 SCR-A-005 ~ SCR-A-010.
 * 하단 탭 '채팅' = 상담함. 설정은 상담함 톱니 → 채팅 설정(시작 화면 · 챗봇 답변 · 자주 쓰는 답변).
 * 상담함 · 채팅방은 화면 스크립트(admin/chat.js)가 같은 템플릿의 조각(rooms · messages)을 짧은 주기로 다시 받아 바꾼다.
 * 저장 · 전송은 fetch 로 보내고 {ok} 또는 {message} 를 받는다.
 * /{id} 보다 리터럴 경로(list·settings·entry·answers·replies·rooms·search·files)가 우선 매칭된다.
 * 화면 확인용 ?tab= · ?state= 는 /dev/admin/chat/... 미리 보기에서 더미 데이터로 동작한다.
 */
@Controller
@RequestMapping("/admin/chats")
@RequiredArgsConstructor
public class AdminChatController {

	private final ChatService chatService;
	private final ChatSettingService settingService;
	private final ChatbotAdminService chatbotAdminService;
	private final SavedReplyService savedReplyService;
	private final CodeService codeService;

	/* ==================== 상담함 ==================== */

	/** 상담함 (대기 · 상담 중 · 종료 · 챗봇) */
	@GetMapping
	public String list(Model model) {
		model.addAttribute("rooms", roomRows());
		return "admin/chat/list";
	}

	/** 상담함 목록 조각 (폴링) */
	@GetMapping("/rooms")
	public String rooms(Model model) {
		model.addAttribute("rooms", roomRows());
		return "admin/chat/list :: rooms";
	}

	/** 상담함 검색 (이름 · 휴대폰 · 대화 내용) */
	@GetMapping("/search")
	@ResponseBody
	public Map<String, Object> search(@RequestParam(defaultValue = "") String q) {
		return Map.of("memberIds", chatService.search(q));
	}

	/** 예전 채팅 내역 주소 → 상담함 */
	@GetMapping("/list")
	public String legacyList() {
		return "redirect:/admin/chats";
	}

	/* ==================== 채팅방 ==================== */

	/** 채팅방 — {id} = MEMBER_ID */
	@GetMapping("/{id}")
	public String detail(@PathVariable("id") Long id, Model model) {
		ChatService.AdminRoom room = chatService.adminRoom(id);
		if (room == null) {
			return "redirect:/admin/chats";
		}
		model.addAttribute("chat", room);
		model.addAttribute("savedReplies", savedReplyService.list());
		return "admin/chat/detail";
	}

	/** 채팅방 메시지 조각 (폴링 · 전송 후) */
	@GetMapping("/{id}/messages")
	public String messages(@PathVariable("id") Long id, Model model) {
		ChatService.AdminRoom room = chatService.adminRoom(id);
		if (room == null) {
			return "redirect:/admin/chats";
		}
		model.addAttribute("chat", room);
		return "admin/chat/detail :: messages";
	}

	@PostMapping("/{id}/messages")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> send(@PathVariable("id") Long id,
			@RequestParam(defaultValue = "") String text, HttpSession session) {
		return run(() -> chatService.adminSend(admin(session).adminId(), id, text, null));
	}

	@PostMapping("/{id}/files")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendFile(@PathVariable("id") Long id,
			@RequestParam("file") MultipartFile file, HttpSession session) {
		return run(() -> chatService.adminSend(admin(session).adminId(), id, "", file));
	}

	/** 상담 종료 */
	@PostMapping("/{id}/end")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> end(@PathVariable("id") Long id, HttpSession session) {
		return run(() -> chatService.end(admin(session).adminId(), id));
	}

	@GetMapping("/files/{messageId}")
	public ResponseEntity<FileSystemResource> file(@PathVariable Long messageId) throws IOException {
		return ChatController.fileResponse(chatService, chatService.adminFile(messageId));
	}

	/* ==================== 채팅 설정 ==================== */

	@GetMapping("/settings")
	public String settings(Model model) {
		model.addAttribute("setting", settingService.get());
		model.addAttribute("replyCount", savedReplyService.list().size());
		return "admin/chat/settings";
	}

	/** 토글 · 시간 · 안내 문구를 바꿀 때마다 저장 */
	@PostMapping("/settings")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveSettings(@RequestParam(defaultValue = "true") boolean bot,
			@RequestParam(defaultValue = "true") boolean consult, @RequestParam String start, @RequestParam String end,
			@RequestParam(defaultValue = "") String delay, @RequestParam(defaultValue = "") String away,
			HttpSession session) {
		return run(() -> settingService.saveOperation(bot, consult, start, end, delay, away, admin(session).adminId()));
	}

	/* ==================== 채팅 시작 화면 ==================== */

	@GetMapping("/entry")
	public String entry(Model model) {
		model.addAttribute("greeting", settingService.get().getGreeting());
		model.addAttribute("topicData", topicChoices());
		model.addAttribute("menuData", menuData(chatbotAdminService.quickMenus()));
		return "admin/chat/entry";
	}

	/** 저장 요청: {greeting, menus: [{id, topicId, label, active}]} */
	public record EntryRequest(String greeting, List<MenuRequest> menus) {
	}

	public record MenuRequest(Long id, Long topicId, String label, boolean active) {
	}

	@PostMapping("/entry")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveEntry(@RequestBody EntryRequest req, HttpSession session) {
		Long adminId = admin(session).adminId();
		try {
			settingService.saveGreeting(req.greeting(), adminId);
			List<ChatbotAdminService.MenuForm> forms = (req.menus() == null ? List.<MenuRequest>of() : req.menus()).stream()
					.map(m -> new ChatbotAdminService.MenuForm(m.id(), m.topicId(), m.label(), m.active())).toList();
			return ResponseEntity.ok(Map.of("menus", menuData(chatbotAdminService.saveQuickMenus(forms, adminId))));
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
	}

	/* ==================== 챗봇 답변 관리 ==================== */

	@GetMapping("/answers")
	public String answers(Model model) {
		model.addAttribute("topicData", chatbotAdminService.topics().stream().map(this::topicData).toList());
		return "admin/chat/answers";
	}

	/** 저장 요청 (화면 값: 답변 방식 · 버튼 동작은 공통코드 이름, 이동할 화면은 화면 이름) */
	public record TopicRequest(Long id, String title, String intro, String type, boolean active,
			List<RefRequest> refs, List<ActionRequest> actions) {
	}

	public record RefRequest(String keywords, String content) {
	}

	public record ActionRequest(String label, String type, String value) {
	}

	@PostMapping("/answers")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveTopic(@RequestBody TopicRequest req, HttpSession session) {
		try {
			List<Lorebook> lorebooks = new ArrayList<>();
			for (RefRequest r : req.refs() == null ? List.<RefRequest>of() : req.refs()) {
				Lorebook lb = new Lorebook();
				lb.setKeywords(r.keywords());
				lb.setContent(r.content());
				lorebooks.add(lb);
			}
			List<ChatbotAction> actions = new ArrayList<>();
			for (ActionRequest a : req.actions() == null ? List.<ActionRequest>of() : req.actions()) {
				ChatbotAction action = new ChatbotAction();
				action.setLabel(a.label());
				action.setActionType(codeOf("ACTION_TYPE", a.type()));
				action.setActionValue(ChatbotAction.LINK.equals(action.getActionType()) ? ChatScreens.toUrl(a.value()) : a.value());
				actions.add(action);
			}
			ChatbotTopic saved = chatbotAdminService.saveTopic(new ChatbotAdminService.TopicForm(req.id(), req.title(),
					req.intro(), codeOf("TOPIC_TYPE", req.type()), req.active(), lorebooks, actions), admin(session).adminId());
			return ResponseEntity.ok(Map.of("topic", topicData(saved)));
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
	}

	/** ⋯ 메뉴: 사용 / 사용 안 함 (바로 적용) */
	@PostMapping("/answers/{topicId}/active")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> setTopicActive(@PathVariable Long topicId,
			@RequestParam boolean active, HttpSession session) {
		return run(() -> chatbotAdminService.setActive(topicId, active, admin(session).adminId()));
	}

	@PostMapping("/answers/{topicId}/delete")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> deleteTopic(@PathVariable Long topicId) {
		return run(() -> chatbotAdminService.deleteTopic(topicId));
	}

	/* ==================== 자주 쓰는 답변 ==================== */

	/** 자주 쓰는 답변 (TBL_SAVED_REPLY) */
	@GetMapping("/replies")
	public String replies(Model model) {
		model.addAttribute("replyData", savedReplyService.list().stream().map(AdminChatController::replyData).toList());
		return "admin/chat/replies";
	}

	@PostMapping("/replies")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveReply(@RequestParam(required = false) Long id,
			@RequestParam(defaultValue = "") String title, @RequestParam(defaultValue = "") String content,
			HttpSession session) {
		try {
			SavedReply reply = savedReplyService.save(id, title, content, admin(session).adminId());
			return ResponseEntity.ok(Map.of("reply", replyData(reply)));
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
	}

	@PostMapping("/replies/{id}/delete")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> deleteReply(@PathVariable Long id) {
		return run(() -> savedReplyService.delete(id));
	}

	/* ==================== 화면 데이터 ==================== */

	/** 상담함 행 (템플릿 data-* 속성 그대로) */
	private List<Map<String, Object>> roomRows() {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (ChatRoom r : chatService.rooms()) {
			Map<String, Object> row = new LinkedHashMap<>();
			String name = r.getMemberName() == null || r.getMemberName().isEmpty() ? "고객" : r.getMemberName();
			row.put("memberId", r.getMemberId());
			row.put("name", name);
			row.put("initial", name.substring(0, 1));
			row.put("msg", r.getLastContent() != null ? r.getLastContent()
					: r.getLastAttachmentName() != null ? r.getLastAttachmentName() : "");
			row.put("time", ChatTimes.listLabel(r.getLastSentAt() != null ? r.getLastSentAt() : r.getStartedAt()));
			row.put("status", r.getStatus());
			row.put("unread", r.getUnreadCount());
			row.put("activity", ChatTimes.epoch(r.getLastSentAt() != null ? r.getLastSentAt() : r.getStartedAt()));
			row.put("wait", ChatRoom.WAITING.equals(r.getStatus()) ? ChatTimes.minutesSince(r.getWaitingAt()) : 0);
			row.put("phone", r.getPhone() != null ? r.getPhone() : "");
			rows.add(row);
		}
		return rows;
	}

	/** 퀵메뉴 후보 주제: {id, title, intro, inactive} */
	private List<Map<String, Object>> topicChoices() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (ChatbotTopic t : chatbotAdminService.topics()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", t.getTopicId());
			m.put("title", t.getTitle());
			m.put("intro", t.getIntro() != null ? t.getIntro() : "");
			m.put("inactive", !t.isActiveTopic());
			list.add(m);
		}
		return list;
	}

	/** 퀵메뉴: {id, topicId, label, active} */
	private static List<Map<String, Object>> menuData(List<QuickMenu> menus) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (QuickMenu q : menus) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", q.getMenuId());
			m.put("topicId", q.getTopicId());
			m.put("label", q.getMenuName());
			m.put("active", "Y".equals(q.getIsActive()));
			list.add(m);
		}
		return list;
	}

	/** 답변 관리 화면 형식: {id, title, intro, type, active, refs: [{keywords, content}], actions: [{label, type, value}]} */
	private Map<String, Object> topicData(ChatbotTopic t) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", t.getTopicId());
		m.put("title", t.getTitle());
		m.put("intro", t.getIntro() != null ? t.getIntro() : "");
		m.put("type", nameOf("TOPIC_TYPE", t.getTopicType()));
		m.put("active", t.isActiveTopic());
		List<Map<String, Object>> refs = new ArrayList<>();
		for (Lorebook lb : t.getLorebooks()) {
			refs.add(Map.of("keywords", lb.getKeywords(), "content", lb.getContent()));
		}
		m.put("refs", refs);
		List<Map<String, Object>> actions = new ArrayList<>();
		for (ChatbotAction a : t.getActions()) {
			String value = a.getActionValue() == null ? ""
					: ChatbotAction.LINK.equals(a.getActionType()) ? ChatScreens.toName(a.getActionValue()) : a.getActionValue();
			actions.add(Map.of("label", a.getLabel(), "type", nameOf("ACTION_TYPE", a.getActionType()), "value", value));
		}
		m.put("actions", actions);
		return m;
	}

	private static Map<String, Object> replyData(SavedReply r) {
		return Map.of("id", r.getReplyId(), "title", r.getTitle(), "content", r.getContent());
	}

	private String nameOf(String group, String code) {
		String name = codeService.name(group, code);
		return name != null ? name : code;
	}

	/** 공통코드 이름 → 코드 (이미 코드면 그대로) */
	private String codeOf(String group, String nameOrCode) {
		if (nameOrCode == null) {
			return null;
		}
		return codeService.list(group).stream()
				.filter(c -> c.getCodeName().equals(nameOrCode) || c.getCode().equals(nameOrCode))
				.map(Code::getCode).findFirst().orElse(nameOrCode);
	}

	private static LoginAdmin admin(HttpSession session) {
		return (LoginAdmin) session.getAttribute(AdminAuthController.ADMIN_KEY);
	}

	private static ResponseEntity<Map<String, Object>> run(Runnable action) {
		try {
			action.run();
			return ResponseEntity.ok(Map.of("ok", true));
		} catch (IllegalArgumentException | IllegalStateException e) {
			return fail(e.getMessage());
		}
	}

	private static ResponseEntity<Map<String, Object>> fail(String message) {
		return ResponseEntity.badRequest().body(Map.of("message", message));
	}
}
