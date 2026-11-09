package com.codemove.web.user;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.codemove.member.LoginMember;
import com.codemove.reservation.HistoryService;
import com.codemove.review.HomeReview;
import com.codemove.review.ReviewService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 홈 (SCR-U-001~002).
 * 앱 진입점: 로그인하지 않았으면 온보딩으로 보낸다 (둘러보기 ?state=guest 만 비회원 허용).
 * 로그인 회원은 세션의 LOGIN_KEY(LoginMember). 상태 카드는 회원의 견적·예약 상태 (HistoryService.home):
 * 견적 도착 > 다가오는 예약 확정 > 검토 중 > 견적 없음. ?state= 가 있으면 그 상태로 미리보기 (예시 값).
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

	public static final String LOGIN_KEY = "loginMember";

	private final HistoryService historyService;
	private final ReviewService reviewService;

	@GetMapping("/")
	public String home(@RequestParam(required = false) String state, HttpSession session, Model model) {
		LoginMember member = (LoginMember) session.getAttribute(LOGIN_KEY);
		if (member == null && !"guest".equals(state)) {
			// 비회원은 둘러보기 홈(?state=guest)만 볼 수 있다
			return "redirect:/onboarding";
		}
		boolean preview = state != null;
		if (state == null) {
			HistoryService.Home home = historyService.home(member.memberId());
			state = home.state();
			if (home.booking() != null) {
				model.addAttribute("booking", home.booking());
			}
		}
		model.addAttribute("state", state);
		model.addAttribute("userName", member != null ? member.name() : null);
		// 후기 카드: 최근 후기 3개. ?state= 미리보기(guest 제외)는 캡처와 같은 예시
		model.addAttribute("reviews", preview && !"guest".equals(state) ? SAMPLE_REVIEWS : reviewService.latestForHome(3));
		return "user/home";
	}

	private static final List<HomeReview> SAMPLE_REVIEWS = List.of(
				new HomeReview("김코드", "이용 2회차 · 어제", "원룸(반포장)", "★★★★★",
						"친절하게 응대해주시고, 주의사항이나 필요한 부분 꼼꼼히 체크해주셔서 이사 잘 할 수 있었습니다."),
				new HomeReview("박이사", "이용 1회차 · 3일 전", "아파트(일반포장)", "★★★★★",
						"영상 견적 서비스 덕분에 방문 없이 바로 가격을 받을 수 있어서 너무 편했어요. 당일 기사님도 시간 약속 철저히 지켜주시고 물건 하나도 흠집 없이 옮겨주셨습니다."),
				new HomeReview("이무브", "이용 3회차 · 1주 전", "빌라(반포장)", "★★★★☆",
						"가격 대비 정말 만족스러웠어요. 다음 이사도 명품익스프레스로 할 것 같습니다. 앱이 직관적이어서 견적 받는 것도 금방 끝났어요."));
}
