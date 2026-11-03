package com.codemove.web.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 채팅 (G-G01~G07).
 * 화면 개발 단계: 퀵메뉴 답변·메시지 전송·파일 첨부는 chat.js 에서 시뮬레이션하고, ?state= 로 캡처 상태를 바로 연다.
 */
@Controller
public class ChatController {

	@GetMapping("/chat")
	public String chat() {
		return "user/chat/index";
	}
}
