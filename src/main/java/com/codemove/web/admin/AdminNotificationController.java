package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 알림 관리 SCR-A-013 ~ SCR-A-015.
 * 화면 개발 단계: 더미 데이터는 템플릿에 있음. 화면 상태는 ?state= 로 확인.
 */
@Controller
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

	@GetMapping
	public String home() {
		return "admin/notification/home";
	}

	@GetMapping("/history")
	public String history() {
		return "admin/notification/history";
	}

	@GetMapping("/settings")
	public String settings() {
		return "admin/notification/settings";
	}
}
