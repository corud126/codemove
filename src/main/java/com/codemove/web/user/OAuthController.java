package com.codemove.web.user;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codemove.domain.Member;
import com.codemove.member.LoginMember;
import com.codemove.member.MemberService;
import com.codemove.member.SocialAccount;
import com.codemove.member.oauth.OAuthClient;
import com.codemove.member.oauth.OAuthProvider;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 간편 로그인 (G-A04 카카오 · 네이버).
 * 연결된 회원이 있으면 바로 로그인, 처음 보는 소셜 계정이면 회원가입 본인인증(/signup)으로 보낸다.
 * 본인인증한 번호가 이미 가입돼 있으면 그 회원에 연결하고, 아니면 새로 가입하며 연결한다 (AuthController).
 * 앱 키가 없거나 사용자가 취소·실패하면 로그인 방법 선택으로 돌아간다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class OAuthController {

	private static final String STATE_KEY = "oauthState";

	private final OAuthClient oauthClient;
	private final MemberService memberService;

	@GetMapping("/login/oauth/{provider}")
	public String start(@PathVariable String provider, HttpSession session) {
		OAuthProvider p = OAuthProvider.fromPath(provider).orElse(null);
		if (p == null || !oauthClient.isEnabled(p)) {
			log.warn("간편 로그인 키 미설정: {}", provider);
			return "redirect:/login";
		}
		String state = UUID.randomUUID().toString();
		session.setAttribute(STATE_KEY, state);
		return "redirect:" + oauthClient.authorizeUrl(p, callbackUrl(p), state);
	}

	@GetMapping("/login/oauth/{provider}/callback")
	public String callback(@PathVariable String provider, @RequestParam(required = false) String code,
			@RequestParam(required = false) String state, HttpSession session) {
		OAuthProvider p = OAuthProvider.fromPath(provider).orElse(null);
		Object expected = session.getAttribute(STATE_KEY);
		session.removeAttribute(STATE_KEY);
		// 취소(code 없음) · 위조 요청(state 불일치)
		if (p == null || !oauthClient.isEnabled(p) || code == null || expected == null || !expected.equals(state)) {
			return "redirect:/login";
		}

		SocialAccount account;
		try {
			account = oauthClient.fetchAccount(p, code, callbackUrl(p), state);
		} catch (RestClientException | IllegalStateException e) {
			log.warn("간편 로그인 실패: {}", provider, e);
			return "redirect:/login";
		}

		Member member = memberService.findActiveBySocial(account);
		if (member != null) {
			session.removeAttribute(AuthController.PENDING_SOCIAL_KEY);
			session.setAttribute(HomeController.LOGIN_KEY, LoginMember.of(member));
			return "redirect:/";
		}
		session.setAttribute(AuthController.PENDING_SOCIAL_KEY, account);
		return "redirect:/signup";
	}

	/** 제공자 앱 설정에 등록할 Redirect URI 와 같아야 한다 (예: http://localhost:8080/login/oauth/kakao/callback) */
	private static String callbackUrl(OAuthProvider p) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/login/oauth/{provider}/callback").buildAndExpand(p.path()).toUriString();
	}
}
