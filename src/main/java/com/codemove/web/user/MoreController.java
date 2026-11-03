package com.codemove.web.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 더보기 화면 그룹 (G-H01~H14): 더보기, 내 정보 수정, 견적내역, 예약내역, 후기, 후기 작성, 알림.
 * 화면 개발 단계: 더미 데이터는 템플릿에 있고, ?state= 로 캡처 상태를 바로 연다.
 */
@Controller
public class MoreController {

	@GetMapping("/more")
	public String more() {
		return "user/more/index";
	}

	@GetMapping("/more/profile")
	public String profile() {
		return "user/more/profile";
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
