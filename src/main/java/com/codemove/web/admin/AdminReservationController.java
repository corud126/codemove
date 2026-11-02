package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 예약 관리 SCR-A-011.
 * 화면 개발 단계: 더미 데이터는 템플릿에 있음. 상태는 ?state= 로 확인.
 */
@Controller
@RequestMapping("/admin/reservations")
public class AdminReservationController {

	@GetMapping
	public String list() {
		return "admin/reservation/list";
	}
}
