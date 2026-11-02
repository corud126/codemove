package com.codemove.web.admin;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 홈(대시보드) SCR-A-016.
 * 화면 개발 단계: 더미 데이터. DB 연동 시 예약·리뷰·견적 집계 쿼리로 대체.
 */
@Controller
@RequestMapping("/admin")
public class AdminHomeController {

	@GetMapping
	public String home(Model model) {
		model.addAttribute("stats", List.of(
				new Stat("전체 예약 신청", 30, "calendar", "/admin/reservations"),
				new Stat("리뷰 등록 현황", 12, "star", "/admin/reviews"),
				new Stat("견적 검토", 4, "document", "/admin/estimates"),
				new Stat("예약 완료", 25, "truck", "/admin/reservations")));
		model.addAttribute("weeklyTotal", 10);
		model.addAttribute("recentReservations", List.of(
				new RecentReservation("홍길동", "26.09.27", "서울 강남구 테헤란로 521", "서울 서초구 남부순환로 2406", "예약 취소"),
				new RecentReservation("정하윤", "26.09.27", "서울 마포구 월드컵북로 396", "경기 고양시 일산동구 중앙로 1275", "예약 완료"),
				new RecentReservation("김명품", "26.09.25", "서울 송파구 올림픽로 300", "서울 강동구 천호대로 1017", "예약 완료")));
		return "admin/home";
	}

	record Stat(String label, int value, String icon, String link) {
	}

	record RecentReservation(String name, String movingDate, String origin, String destination, String status) {
	}
}
