package com.codemove.web.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.chat.ChatService;
import com.codemove.chat.ChatSettingService;
import com.codemove.chat.ChatbotAdminService;
import com.codemove.domain.ChatMessage;
import com.codemove.domain.ChatSetting;
import com.codemove.member.LoginMember;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 채팅 (G-G01~G07).
 * 첫 화면: 인사말 · 퀵메뉴 · 운영시간은 DB(TBL_CHAT_SETTING · TBL_QUICK_MENU).
 * 메시지는 chat.js 가 fetch 로 주고받고, 상담사 답장은 짧은 주기로 다시 불러온다(GET /chat/messages?after=).
 * /dev/user/chat/index 미리 보기(컨트롤러 없음)에서는 chat.js 가 예전처럼 시뮬레이션하고 ?state= 로 캡처 상태를 연다.
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;
	private final ChatSettingService settingService;
	private final ChatbotAdminService chatbotAdminService;

	@GetMapping("/chat")
	public String chat(Model model) {
		ChatSetting setting = settingService.get();
		model.addAttribute("live", true);
		model.addAttribute("greeting", setting.getGreeting());
		model.addAttribute("hours", ChatSettingService.hoursText(setting));
		// 챗봇을 끄면 퀵메뉴는 숨기고 [상담사 연결]만
		model.addAttribute("quickMenus", setting.isBotOn() ? chatbotAdminService.visibleQuickMenus() : List.of());
		return "user/chat/index";
	}

	/** after=0: 가장 최근 방의 대화, after>0: 그 뒤에 온 메시지 */
	@GetMapping("/chat/messages")
	@ResponseBody
	public ChatService.MemberChat messages(@RequestParam(defaultValue = "0") long after, HttpSession session) {
		Long memberId = member(session).memberId();
		return after > 0 ? chatService.poll(memberId, after) : chatService.open(memberId);
	}

	@PostMapping("/chat/messages")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> send(@RequestParam(defaultValue = "") String text,
			@RequestParam(required = false) Long topicId, HttpSession session) {
		try {
			return ResponseEntity.ok(Map.of("messages", chatService.ask(member(session), text, topicId)));
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
	}

	/** [상담사 연결 요청] */
	@PostMapping("/chat/agent")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> agent(HttpSession session) {
		return ResponseEntity.ok(Map.of("messages", chatService.requestAgent(member(session))));
	}

	@PostMapping("/chat/files")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "") String text, HttpSession session) {
		try {
			return ResponseEntity.ok(Map.of("messages", chatService.sendFile(member(session), file, text)));
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
	}

	/** 첨부 파일 보기 · 내려받기 (본인 대화만) */
	@GetMapping("/chat/files/{messageId}")
	public ResponseEntity<FileSystemResource> file(@PathVariable Long messageId, HttpSession session) throws IOException {
		ChatMessage m = chatService.memberFile(member(session).memberId(), messageId);
		return fileResponse(chatService, m);
	}

	/** 채팅 첨부 응답 (사진은 바로 보기, 나머지는 내려받기) — 관리자 컨트롤러도 사용 */
	public static ResponseEntity<FileSystemResource> fileResponse(ChatService chatService, ChatMessage m)
			throws IOException {
		Path path = m == null ? null : chatService.filePath(m);
		if (path == null || !Files.isRegularFile(path)) {
			return ResponseEntity.notFound().build();
		}
		MediaType type = MediaTypeFactory.getMediaType(m.getAttachmentName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		ContentDisposition disposition = (m.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(m.getAttachmentName(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok().contentType(type).contentLength(Files.size(path))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(new FileSystemResource(path));
	}

	/** /chat/** 은 LoginCheckInterceptor 가 막지 않으므로(/chat 만 등록) 여기서 로그인 확인 */
	private static LoginMember member(HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		if (member == null) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return member;
	}

	private static ResponseEntity<Map<String, Object>> fail(String message) {
		return ResponseEntity.badRequest().body(Map.of("message", message));
	}
}
