package com.codemove.web.user;

import java.util.Map;

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
import com.codemove.domain.Social;
import com.codemove.member.LoginMember;
import com.codemove.member.MemberService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 더보기 화면 그룹 (G-H01~H14): 더보기, 내 정보 수정, 견적내역, 예약내역, 후기, 후기 작성, 알림.
 * 내 정보 수정(휴대폰 번호 변경 포함)·탈퇴는 DB 연동. 나머지는 더미 데이터가 템플릿에 있고, ?state= 로 캡처 상태를 바로 연다.
 */
@Controller
@RequiredArgsConstructor
public class MoreController {

	/** 휴대폰 번호 변경 인증 (PhoneVerifier purpose) */
	private static final String PHONE_CHANGE = "phoneChange";

	private final MemberService memberService;
	private final PhoneVerifier phoneVerifier;

	@GetMapping("/more")
	public String more() {
		return "user/more/index";
	}

	/** 내 정보 수정: 이름 · 번호 · 연결된 간편 로그인 · 가입일 (휴대폰 번호 변경은 아래 /more/profile/phone) */
	@GetMapping("/more/profile")
	public String profile(HttpSession session, Model model) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		Member member = memberService.findActiveById(login.memberId());
		if (member == null) {
			session.invalidate();
			return "redirect:/onboarding";
		}
		model.addAttribute("member", member);
		model.addAttribute("loginTypes", memberService.socials(member.getMemberId()).stream()
				.map(Social::getLoginType).toList());
		return "user/more/profile";
	}

	/** 휴대폰 번호 변경 인증번호 발송 (G-H03): 새 번호가 형식에 맞고 다른 회원이 쓰지 않을 때만 */
	@PostMapping("/more/profile/phone/send")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendPhoneCode(@RequestParam(defaultValue = "") String phone,
			HttpSession session) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		String normalized = MemberService.normalizePhone(phone);
		if (normalized == null) {
			return phoneFail(HttpStatus.BAD_REQUEST, "휴대폰 번호를 다시 확인해 주세요.");
		}
		Member owner = memberService.findActiveByPhone(normalized);
		if (owner != null && owner.getMemberId().equals(login.memberId())) {
			return phoneFail(HttpStatus.BAD_REQUEST, "지금 쓰고 있는 번호예요.");
		}
		if (memberService.isRegistered(normalized)) {
			return phoneFail(HttpStatus.CONFLICT, "이미 가입된 번호예요. 다른 번호를 입력해 주세요.");
		}
		phoneVerifier.send(session, PHONE_CHANGE, normalized);
		return ResponseEntity.ok(Map.of("seconds", PhoneVerifier.TTL.toSeconds(), "phone", normalized));
	}

	/** 휴대폰 번호 변경 (G-H04): 인증번호가 맞으면 번호를 바꾸고 로그인 정보(세션)도 새 번호로 */
	@PostMapping("/more/profile/phone")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> changePhone(@RequestParam(defaultValue = "") String phone,
			@RequestParam(defaultValue = "") String code, HttpSession session) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		String normalized = MemberService.normalizePhone(phone);
		switch (phoneVerifier.verify(session, PHONE_CHANGE, normalized, code)) {
			case MISMATCH:
				return phoneFail(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요. 다시 확인해 주세요.");
			case EXPIRED:
				return phoneFail(HttpStatus.GONE, "인증번호가 만료되었어요. 다시 받아 주세요.");
			default:
				break;
		}
		Member member;
		try {
			member = memberService.changePhone(login.memberId(), normalized);
		} catch (IllegalStateException e) {
			return phoneFail(HttpStatus.CONFLICT, e.getMessage());
		}
		if (member == null) {
			session.invalidate();
			return ResponseEntity.ok(Map.of("redirect", "/onboarding"));
		}
		session.setAttribute(HomeController.LOGIN_KEY, LoginMember.of(member));
		return ResponseEntity.ok(Map.of("phone", member.getPhone()));
	}

	private static ResponseEntity<Map<String, Object>> phoneFail(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("message", message));
	}

	/** 회원 탈퇴: 이름·번호 파기 후 로그아웃 (MemberService.withdraw) */
	@PostMapping("/more/withdraw")
	public String withdraw(HttpSession session) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		memberService.withdraw(login.memberId());
		session.invalidate();
		return "redirect:/onboarding";
	}

	@GetMapping("/more/estimates")
	public String estimates() {
		return "user/more/estimates";
	}

	@GetMapping("/more/reservations")
	public String reservations() {
		return "user/more/reservations";
	}

	@GetMapping("/more/reviews")
	public String reviews() {
		return "user/more/reviews";
	}

	@GetMapping("/more/reviews/new")
	public String reviewNew() {
		return "user/more/review-new";
	}

	@GetMapping("/notifications")
	public String notifications() {
		return "user/more/notifications";
	}
}
