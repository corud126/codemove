package com.codemove.web.user;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

/**
 * 온보딩 · 로그인 · 회원가입 (G-A01~A13 / 쉬운 모드 E-A01~A13).
 * 화면 개발 단계: 템플릿이 ?state= 로 화면 상태를 바꾼다. 인증·가입 처리는 DB 연동 단계에서 추가.
 */
@Controller
public class AuthController {

	/** 온보딩 1~3 (G-A01~A03) */
	@GetMapping("/onboarding")
	public String onboarding() {
		return "user/auth/onboarding";
	}

	/** 로그인 방법 선택 (G-A04): 온보딩 위에 바텀시트를 연 상태 */
	@GetMapping("/login")
	public String login(Model model) {
		model.addAttribute("state", "login");
		return "user/auth/onboarding";
	}

	/** 휴대전화 로그인 (G-A05~A06) */
	@GetMapping("/login/phone")
	public String loginPhone() {
		return "user/auth/login-phone";
	}

	/** 회원가입 본인인증 정보 (G-A07, G-A10) */
	@GetMapping("/signup")
	public String signup() {
		return "user/auth/signup";
	}

	/** 개인정보 수집 안내 (G-A08) */
	@GetMapping("/signup/terms")
	public String terms() {
		return "user/auth/terms";
	}

	/** 인증번호 입력 (G-A11~A12) */
	@GetMapping("/signup/verify")
	public String verify() {
		return "user/auth/verify";
	}

	/** 가입 완료 (G-A13) */
	@GetMapping("/signup/welcome")
	public String welcome() {
		return "user/auth/welcome";
	}

	/** 로그인 완료(휴대전화 로그인 · 가입 완료 '시작하기'): 화면 개발 단계에서는 세션에 로그인 표시만 남긴다 */
	@GetMapping("/login/complete")
	public String loginComplete(HttpSession session) {
		session.setAttribute(HomeController.LOGIN_KEY, Boolean.TRUE);
		return "redirect:/";
	}

	/** 로그아웃 → 온보딩 */
	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/onboarding";
	}
}
