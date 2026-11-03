package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 일정 SCR-A-020 예약 일정(예약을 달력으로 조회, 보기 전용) · SCR-A-022 일정 관리(설정: 하루 최대 예약 · 정기/공휴일/임시 휴무, 연동: 공휴일 · 손 없는 날).
 * 일정 관리의 설정은 사용자 견적 날짜 선택(휴무일 · 마감일 선택 불가)과 연결된다. 개인 일정 등록(SCR-A-021)은 뺐다.
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
