package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 알림 관리 SCR-A-013 ~ SCR-A-015 — 한 화면(발송 내역 · 알림 설정 탭).
 * 화면 개발 단계: 더미 데이터는 템플릿·JS 에 있음. 탭은 ?tab=history|settings, 화면 상태는 ?state= 로 확인.
 */
@Controller
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

	@GetMapping
	public String home() {
		return "admin/notification/home";
	}

	/** 예전 알림 내역 주소 → 발송 내역 탭 */
	@GetMapping("/history")
	public String history() {
		return "redirect:/admin/notifications";
	}

	/** 예전 메시징 운영 설정 주소 → 알림 설정 탭 */
	@GetMapping("/settings")
	public String settings() {
		return "redirect:/admin/notifications?tab=settings";
	}
}
