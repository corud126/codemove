package com.codemove.web.admin;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.codemove.dashboard.DashboardService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 홈(대시보드) SCR-A-016.
 * 예약·리뷰·견적 집계는 DashboardService (읽기 전용 쿼리).
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHomeController {

	private final DashboardService dashboardService;

	@GetMapping
	public String home(Model model) {
		DashboardService.View view = dashboardService.view(LocalDate.now());
		model.addAttribute("today", view.today());
		model.addAttribute("stats", view.stats());
		model.addAttribute("weeklyTotal", view.weeklyTotal());
		model.addAttribute("chart", view.chart());
		model.addAttribute("recentReservations", view.recent());
		return "admin/home";
	}
}
