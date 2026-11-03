package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 채팅 SCR-A-005 ~ SCR-A-010.
 * 하단 탭 '채팅' = 상담함. 설정은 상담함 톱니 → 채팅 설정(시작 화면 · 챗봇 답변 · 자주 쓰는 답변).
 * 화면 개발 단계: 더미 데이터는 템플릿·JS 에 있음. 화면 상태는 ?tab= · ?state= 로 확인.
 * /{id} 보다 리터럴 경로(list·settings·entry·answers·replies)가 우선 매칭된다.
 */
@Controller
@RequestMapping("/admin/chats")
public class AdminChatController {

	/** 상담함 (대기 · 상담 중 · 종료 · 챗봇) */
	@GetMapping
	public String list() {
		return "admin/chat/list";
	}

	/** 예전 채팅 내역 주소 → 상담함 */
	@GetMapping("/list")
	public String legacyList() {
		return "redirect:/admin/chats";
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

	/** 자주 쓰는 답변 (TBL_SAVED_REPLY, ERD 반영 대기) */
	@GetMapping("/replies")
	public String replies() {
		return "admin/chat/replies";
	}

	/** 채팅방 — {id} = MEMBER_ID */
	@GetMapping("/{id}")
	public String detail(@PathVariable("id") String id) {
		return "admin/chat/detail";
	}
}
