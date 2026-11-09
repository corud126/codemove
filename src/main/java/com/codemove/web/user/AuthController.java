package com.codemove.web.user;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.common.sms.PhoneVerifier;
import com.codemove.domain.Member;
import com.codemove.member.LoginMember;
import com.codemove.member.MemberService;
import com.codemove.member.SocialAccount;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 온보딩 · 로그인 · 회원가입 (G-A01~A13 / 쉬운 모드 E-A01~A13).
 * 회원가입: 이름·번호 입력(POST /signup) → 인증번호 확인(POST /signup/verify) 시 가입 + 로그인 → 가입 완료.
 * 휴대전화 로그인: 인증번호 발송(POST /login/phone/send) → 확인(POST /login/phone).
 * POST 요청은 화면 스크립트(auth.js)가 fetch 로 보내고 {redirect} 또는 {message} 를 받는다.
 * 간편 로그인으로 처음 들어온 경우(PENDING_SOCIAL_KEY): 본인인증한 번호가 이미 가입돼 있으면 그 회원에 연결 후 홈,
 * 아니면 가입하면서 연결한다 (OAuthController).
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

	/** 회원과 아직 연결되지 않은 간편 로그인 계정 (SocialAccount) */
	public static final String PENDING_SOCIAL_KEY = "pendingSocial";

	private static final String SIGNUP_KEY = "signupForm";
	private static final String SIGNUP = "signup";
	private static final String LOGIN = "login";

	private final MemberService memberService;
	private final PhoneVerifier phoneVerifier;

	/** 본인인증 전 가입 정보 (세션 보관) */
	record SignupForm(String name, String phone, LocalDateTime termsAgreedAt) implements Serializable {
	}

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

	/** 휴대전화 로그인 인증번호 발송: 가입된 번호에만 보낸다 */
	@PostMapping("/login/phone/send")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendLoginCode(@RequestParam(defaultValue = "") String phone,
			HttpSession session) {
		String normalized = MemberService.normalizePhone(phone);
		if (normalized == null) {
			return fail(HttpStatus.BAD_REQUEST, "휴대폰 번호를 다시 확인해 주세요.");
		}
		if (memberService.findActiveByPhone(normalized) == null) {
			return fail(HttpStatus.NOT_FOUND, "가입되지 않은 번호예요. 회원가입 후 이용해 주세요.");
		}
		phoneVerifier.send(session, LOGIN, normalized);
		return ResponseEntity.ok(Map.of("seconds", PhoneVerifier.TTL.toSeconds()));
	}

	@PostMapping("/login/phone")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> loginByPhone(@RequestParam(defaultValue = "") String phone,
			@RequestParam(defaultValue = "") String code, HttpSession session) {
		String normalized = MemberService.normalizePhone(phone);
		ResponseEntity<Map<String, Object>> error = checkCode(session, LOGIN, normalized, code);
		if (error != null) {
			return error;
		}
		Member member = memberService.findActiveByPhone(normalized);
		if (member == null) {
			return fail(HttpStatus.NOT_FOUND, "가입되지 않은 번호예요. 회원가입 후 이용해 주세요.");
		}
		session.setAttribute(HomeController.LOGIN_KEY, LoginMember.of(member));
		return redirect("/");
	}

	/** 회원가입 본인인증 정보 (G-A07, G-A10) */
	@GetMapping("/signup")
	public String signup() {
		return "user/auth/signup";
	}

	/** 이름·번호 확인 후 인증번호 발송 → 인증번호 입력 화면 */
	@PostMapping("/signup")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> startSignup(@RequestParam(defaultValue = "") String name,
			@RequestParam(defaultValue = "") String phone, @RequestParam(defaultValue = "false") boolean agreed,
			HttpSession session) {
		String trimmed = name.trim();
		String normalized = MemberService.normalizePhone(phone);
		if (trimmed.isEmpty() || trimmed.length() > 50) {
			return fail(HttpStatus.BAD_REQUEST, "이름을 다시 확인해 주세요.");
		}
		if (normalized == null) {
			return fail(HttpStatus.BAD_REQUEST, "휴대폰 번호를 다시 확인해 주세요.");
		}
		if (!agreed) {
			return fail(HttpStatus.BAD_REQUEST, "개인정보 수집 내용을 확인해 주세요.");
		}
		// 간편 로그인 연결이면 가입된 번호도 본인인증 후 연결한다
		if (session.getAttribute(PENDING_SOCIAL_KEY) == null && memberService.isRegistered(normalized)) {
			return fail(HttpStatus.CONFLICT, "이미 가입된 번호예요. 휴대전화로 로그인해 주세요.");
		}
		session.setAttribute(SIGNUP_KEY, new SignupForm(trimmed, normalized, LocalDateTime.now()));
		phoneVerifier.send(session, SIGNUP, normalized);
		return redirect("/signup/verify");
	}

	/** 개인정보 수집 안내 (G-A08) */
	@GetMapping("/signup/terms")
	public String terms() {
		return "user/auth/terms";
	}

	/** 인증번호 입력 (G-A11~A12) */
	@GetMapping("/signup/verify")
	public String verify(HttpSession session, Model model) {
		SignupForm form = (SignupForm) session.getAttribute(SIGNUP_KEY);
		if (form == null) {
			return "redirect:/signup";
		}
		model.addAttribute("name", form.name());
		model.addAttribute("phone", form.phone());
		model.addAttribute("seconds", phoneVerifier.remainingSeconds(session, SIGNUP));
		return "user/auth/verify";
	}

	/** 인증번호가 맞으면 가입하고 바로 로그인 (간편 로그인 연결이면 기존 회원에 연결 후 로그인) */
	@PostMapping("/signup/verify")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> completeSignup(@RequestParam(defaultValue = "") String code,
			HttpSession session) {
		SignupForm form = (SignupForm) session.getAttribute(SIGNUP_KEY);
		if (form == null) {
			return redirect("/signup");
		}
		ResponseEntity<Map<String, Object>> error = checkCode(session, SIGNUP, form.phone(), code);
		if (error != null) {
			return error;
		}
		SocialAccount social = (SocialAccount) session.getAttribute(PENDING_SOCIAL_KEY);
		session.removeAttribute(SIGNUP_KEY);
		session.removeAttribute(PENDING_SOCIAL_KEY);
		Member member = social != null ? memberService.findActiveByPhone(form.phone()) : null;
		boolean linkOnly = member != null;
		try {
			if (linkOnly) {
				memberService.linkSocial(member.getMemberId(), social);
			} else {
				member = memberService.join(form.name(), form.phone(), form.termsAgreedAt(), social);
			}
		} catch (DuplicateKeyException e) {
			// 그 사이 같은 번호로 가입됐거나 같은 소셜 계정이 다른 회원에 연결된 경우
			return fail(HttpStatus.CONFLICT, "이미 가입된 정보예요. 휴대전화로 로그인해 주세요.");
		}
		session.setAttribute(HomeController.LOGIN_KEY, LoginMember.of(member));
		return redirect(linkOnly ? "/" : "/signup/welcome");
	}

	/** 인증번호 재발송 */
	@PostMapping("/signup/verify/resend")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> resendSignupCode(HttpSession session) {
		SignupForm form = (SignupForm) session.getAttribute(SIGNUP_KEY);
		if (form == null) {
			return redirect("/signup");
		}
		phoneVerifier.send(session, SIGNUP, form.phone());
		return ResponseEntity.ok(Map.of("seconds", PhoneVerifier.TTL.toSeconds()));
	}

	/** 가입 완료 (G-A13) */
	@GetMapping("/signup/welcome")
	public String welcome(HttpSession session, Model model) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		if (member == null) {
			return "redirect:/onboarding";
		}
		model.addAttribute("name", member.name());
		return "user/auth/welcome";
	}

	/** 로그아웃 → 온보딩 */
	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/onboarding";
	}

	/** 인증번호 확인. 통과하면 null, 아니면 화면에 보여줄 오류 응답 */
	private ResponseEntity<Map<String, Object>> checkCode(HttpSession session, String purpose, String phone, String code) {
		return switch (phoneVerifier.verify(session, purpose, phone, code)) {
			case OK -> null;
			case MISMATCH -> fail(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요. 다시 확인해 주세요.");
			case EXPIRED -> fail(HttpStatus.GONE, "인증번호가 만료되었어요. 다시 받아 주세요.");
		};
	}

	private static ResponseEntity<Map<String, Object>> redirect(String url) {
		return ResponseEntity.ok(Map.of("redirect", url));
	}

	private static ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("message", message));
	}
}
