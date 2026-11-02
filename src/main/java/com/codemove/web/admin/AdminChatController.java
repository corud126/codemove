package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 채팅 관리 SCR-A-005 ~ SCR-A-010.
 * 화면 개발 단계: 더미 데이터는 템플릿에 있음. 화면 상태는 ?state= 로 확인.
 * /{id} 보다 리터럴 경로(list·settings·entry·answers)가 우선 매칭된다.
 */
@Controller
@RequestMapping("/admin/chats")
public class AdminChatController {

	@GetMapping
	public String home() {
		return "admin/chat/home";
	}

	@GetMapping("/list")
	public String list() {
		return "admin/chat/list";
	}

	@GetMapping("/settings")
	public String settings() {
		return "admin/chat/settings";
	}

	@GetMapping("/entry")
	public String entry() {
		return "admin/chat/entry";
	}

	@GetMapping("/answers")
	public String answers() {
		return "admin/chat/answers";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable("id") String id) {
		return "admin/chat/detail";
	}
}
