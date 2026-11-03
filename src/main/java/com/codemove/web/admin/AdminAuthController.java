package com.codemove.web.admin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/**
 * 관리자 스플래시 · 로그인 · 비밀번호 변경 (ERD 수정안 Q10).
 * 화면 개발 단계: 아무 아이디·비밀번호나 통과시키고, 아이디별 비밀번호 변경 여부만 메모리에 기억한다.
 * DB 연동 시 TBL_ADMIN(LOGIN_ID, PASSWORD 해시, PW_CHANGED_AT)으로 대체.
 */
@Controller
@RequestMapping("/admin")
public class AdminAuthController {

	public static final String ADMIN_KEY = "loginAdmin";
	public static final String PW_CHANGED_KEY = "adminPwChanged";

	/** 비밀번호를 바꾼 아이디 (모의 — PW_CHANGED_AT 이 있는 계정) */
	private final Map<String, Boolean> pwChanged = new ConcurrentHashMap<>();

	/** 로그인 (처음 들어오면 스플래시 후 표시) */
	@GetMapping("/login")
	public String loginForm() {
		return "admin/auth/login";
	}

	@PostMapping("/login")
	public String login(@RequestParam(defaultValue = "") String id, @RequestParam(defaultValue = "") String pw,
			HttpSession session) {
		if (id.isBlank() || pw.isEmpty()) {
			return "redirect:/admin/login?state=error";
		}
		boolean changed = pwChanged.getOrDefault(id, false);
		session.setAttribute(ADMIN_KEY, id);
		session.setAttribute(PW_CHANGED_KEY, changed);
		return changed ? "redirect:/admin" : "redirect:/admin/password";
	}

	/** 비밀번호 변경 — 첫 로그인(초기 비밀번호) 시 강제 */
	@GetMapping("/password")
	public String passwordForm() {
		return "admin/auth/password";
	}

	@PostMapping("/password")
	public String changePassword(HttpSession session) {
		pwChanged.put((String) session.getAttribute(ADMIN_KEY), true);
		session.setAttribute(PW_CHANGED_KEY, true);
		return "redirect:/admin";
	}

	/** 로그아웃 → 로그인 (사용자 앱 로그인 상태는 그대로 둔다) */
	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.removeAttribute(ADMIN_KEY);
		session.removeAttribute(PW_CHANGED_KEY);
		return "redirect:/admin/login";
	}
}
