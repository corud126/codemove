package com.codemove.web.user;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/**
 * 사용자 홈 (SCR-U-001~002).
 * 앱 진입점: 로그인하지 않았으면 온보딩으로 보낸다.
 * 화면 개발 단계: 로그인은 세션 표시(LOGIN_KEY)로 흉내 내고, ?state= 로 화면 상태를 바꿔 확인한다(guest 외에는 로그인 필요).
 * DB 연동 시 로그인 회원의 견적·예약 상태로 대체.
 */
@Controller
public class HomeController {

	public static final String LOGIN_KEY = "loginMember";

	@GetMapping("/")
	public String home(@RequestParam(required = false) String state, HttpSession session, Model model) {
		boolean loggedIn = session.getAttribute(LOGIN_KEY) != null;
		if (!loggedIn && !"guest".equals(state)) {
			// 비회원은 둘러보기 홈(?state=guest)만 볼 수 있다
			return "redirect:/onboarding";
		}
		if (state == null) {
			state = "none";
		}
		model.addAttribute("state", state);
		model.addAttribute("userName", "김무브");
		model.addAttribute("reviews", List.of(
				new ReviewCard("김코드", "이용 2회차 · 어제", "원룸(반포장)", "★★★★★",
						"친절하게 응대해주시고, 주의사항이나 필요한 부분 꼼꼼히 체크해주셔서 이사 잘 할 수 있었습니다."),
				new ReviewCard("박이사", "이용 1회차 · 3일 전", "아파트(일반포장)", "★★★★★",
						"영상 견적 서비스 덕분에 방문 없이 바로 가격을 받을 수 있어서 너무 편했어요. 당일 기사님도 시간 약속 철저히 지켜주시고 물건 하나도 흠집 없이 옮겨주셨습니다."),
				new ReviewCard("이무브", "이용 3회차 · 1주 전", "빌라(반포장)", "★★★★☆",
						"가격 대비 정말 만족스러웠어요. 다음 이사도 명품익스프레스로 할 것 같습니다. 앱이 직관적이어서 견적 받는 것도 금방 끝났어요.")));
		return "user/home";
	}

	record ReviewCard(String name, String meta, String type, String stars, String content) {
	}
}
