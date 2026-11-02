package com.codemove.web.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 내 이사 · 견적 상세 · 예약 상세 (G-D, G-E, G-F).
 * 화면 개발 단계: 템플릿이 ${param.tab} / ${param.state} 등으로 상태를 분기한다.
 * DB 연동 시 로그인 회원의 견적·예약 정보를 모델에 담아 더미 문구를 교체한다.
 */
@Controller
public class MoveController {

	/** 내 이사: ?tab=booking|estimate|done, ?state=... */
	@GetMapping("/move")
	public String move() {
		return "user/move/index";
	}

	/** 견적 상세: ?state=arrived|confirmed, ?tab=quote|ai, ?doc=open */
	@GetMapping("/estimates/{id}")
	public String estimateDetail(@PathVariable Long id) {
		return "user/move/estimate-detail";
	}

	/** 예약 상세: ?payment=open, ?edit=true, ?cancel=open */
	@GetMapping("/reservations/{id}")
	public String reservationDetail(@PathVariable Long id) {
		return "user/move/reservation-detail";
	}
}
