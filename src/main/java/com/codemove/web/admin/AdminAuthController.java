package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.codemove.admin.AdminService;
import com.codemove.admin.LoginAdmin;
import com.codemove.domain.Admin;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 스플래시 · 로그인 · 비밀번호 변경 (ERD 수정안 Q10).
 * TBL_ADMIN(LOGIN_ID, PASSWORD BCrypt 해시)으로 확인하고, PW_CHANGED_AT 이 없으면(초기 비밀번호) 변경을 강제한다.
 * 관리자 계정은 화면 없이 DB 에 등록한다 (docs/erd/admin-seed.sql).
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

	public static final String ADMIN_KEY = "loginAdmin";
	public static final String PW_CHANGED_KEY = "adminPwChanged";

	private final AdminService adminService;

	/** 로그인 (처음 들어오면 스플래시 후 표시) */
	@GetMapping("/login")
	public String loginForm() {
		return "admin/auth/login";
	}

	@PostMapping("/login")
	public String login(@RequestParam(defaultValue = "") String id, @RequestParam(defaultValue = "") String pw,
			HttpSession session) {
		Admin admin = id.isBlank() || pw.isEmpty() ? null : adminService.login(id.trim(), pw);
		if (admin == null) {
			return "redirect:/admin/login?state=error";
		}
		boolean changed = admin.getPwChangedAt() != null;
		session.setAttribute(ADMIN_KEY, LoginAdmin.of(admin));
		session.setAttribute(PW_CHANGED_KEY, changed);
		return changed ? "redirect:/admin" : "redirect:/admin/password";
	}

	/** 비밀번호 변경 — 첫 로그인(초기 비밀번호) 시 강제 */
	@GetMapping("/password")
	public String passwordForm() {
		return "admin/auth/password";
	}

	@PostMapping("/password")
	public String changePassword(@RequestParam(defaultValue = "") String current,
			@RequestParam(defaultValue = "") String password, HttpSession session) {
		LoginAdmin admin = (LoginAdmin) session.getAttribute(ADMIN_KEY);
		if (!adminService.changePassword(admin.adminId(), current, password)) {
			return "redirect:/admin/password?state=error";
		}
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
