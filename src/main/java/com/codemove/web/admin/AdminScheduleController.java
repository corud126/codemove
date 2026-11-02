package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 일정 SCR-A-020 일정 캘린더(+ SCR-A-021 일정 등록 시트) · SCR-A-022 일정 관리.
 * 화면 개발 단계: 더미 데이터는 템플릿/JS에 있음. 화면 상태는 ?state= 로 확인.
 */
@Controller
@RequestMapping("/admin/schedule")
public class AdminScheduleController {

	@GetMapping
	public String calendar() {
		return "admin/schedule/calendar";
	}

	@GetMapping("/manage")
	public String manage() {
		return "admin/schedule/manage";
	}
}
