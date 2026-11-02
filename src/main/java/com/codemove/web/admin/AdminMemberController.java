package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 회원 관리 SCR-A-023.
 * 화면 개발 단계: 더미 데이터는 템플릿에 있음. 화면 상태는 ?state= 로 확인.
 */
@Controller
@RequestMapping("/admin/members")
public class AdminMemberController {

	@GetMapping
	public String list() {
		return "admin/member/list";
	}
}
