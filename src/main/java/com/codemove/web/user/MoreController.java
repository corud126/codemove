package com.codemove.web.user;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.codemove.domain.Member;
import com.codemove.domain.Social;
import com.codemove.member.LoginMember;
import com.codemove.member.MemberService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 더보기 화면 그룹 (G-H01~H14): 더보기, 내 정보 수정, 견적내역, 예약내역, 후기, 후기 작성, 알림.
 * 내 정보 수정·탈퇴는 DB 연동. 나머지는 더미 데이터가 템플릿에 있고, ?state= 로 캡처 상태를 바로 연다.
 */
@Controller
@RequiredArgsConstructor
public class MoreController {

	private final MemberService memberService;

	@GetMapping("/more")
	public String more() {
		return "user/more/index";
	}

	/** 내 정보 수정: 이름 · 번호 · 연결된 간편 로그인 · 가입일 (휴대폰 번호 변경은 아직 more.js 시뮬레이션) */
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
