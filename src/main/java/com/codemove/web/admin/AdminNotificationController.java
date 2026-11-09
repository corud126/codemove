package com.codemove.web.admin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.admin.LoginAdmin;
import com.codemove.notification.NotificationService;
import com.codemove.notification.NotificationSettingService;
import com.codemove.notification.NotificationViews;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 알림 관리 SCR-A-013 ~ SCR-A-015 — 한 화면(발송 내역 · 알림 설정 탭) + 알림 패널(A-019) 읽음 처리.
 * 탭은 ?tab=history|settings, 화면 상태는 ?state= 로 확인 (미리보기).
 * 발송 내역: 첫 20건은 화면과 함께, 이후 · 검색 · 필터는 /admin/notifications/page (JSON, 서버 쿼리)
 */
@Controller
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

	private final NotificationService notificationService;
	private final NotificationSettingService settingService;

	@GetMapping
	public String home(HttpSession session, Model model) {
		model.addAttribute("history", notificationService.history(null, null, null, null));
		model.addAttribute("notiSettings", settingService.view(admin(session).adminId()));
		return "admin/notification/home";
	}

	/** 발송 내역 페이지 (q: 이름 또는 날짜, channel: 앱·알림톡·문자, result: held·sms·sent, cursor: 이전 응답의 nextCursor) */
	@GetMapping("/page")
	@ResponseBody
	public NotificationViews.History page(@RequestParam(required = false) String q,
			@RequestParam(required = false) String channel, @RequestParam(required = false) String result,
			@RequestParam(required = false) Long cursor) {
		return notificationService.history(q, channel, result, cursor);
	}

	/** 예전 알림 내역 주소 → 발송 내역 탭 */
	@GetMapping("/history")
	public String historyRedirect() {
		return "redirect:/admin/notifications";
	}

	/** 예전 메시징 운영 설정 주소 → 알림 설정 탭 */
	@GetMapping("/settings")
	public String settings() {
		return "redirect:/admin/notifications?tab=settings";
	}

	/** 고객 발송 설정 저장 (토글 · 보류 시각을 바꿀 때마다) */
	@PostMapping("/settings")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveSettings(@RequestParam boolean enabled, @RequestParam boolean quiet,
			@RequestParam String quietStart, @RequestParam String quietEnd, @RequestParam boolean sms, HttpSession session) {
		try {
			settingService.save(enabled, quiet, quietStart, quietEnd, sms, admin(session).adminId());
			return ResponseEntity.ok(Map.of("ok", true));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	/** 내 알림: 알림 받기 (휴대폰 푸시만, 패널에는 그대로 쌓임) */
	@PostMapping("/push")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> savePush(@RequestParam boolean enabled, HttpSession session) {
		try {
			settingService.savePush(admin(session).adminId(), enabled);
			return ResponseEntity.ok(Map.of("ok", true));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	/** 알림 패널 행 클릭: 읽음 (관리자 공용) */
	@PostMapping("/{id}/read")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> read(@PathVariable Long id) {
		if (!notificationService.markAdminRead(id)) {
			return ResponseEntity.badRequest().body(Map.of("message", "알림을 찾을 수 없습니다."));
		}
		return ResponseEntity.ok(Map.of("ok", true));
	}

	/** 알림 패널 모두 읽음 (관리자 공용) */
	@PostMapping("/read-all")
	@ResponseBody
	public Map<String, Object> readAll() {
		return Map.of("updated", notificationService.markAllAdminRead());
	}

	private static LoginAdmin admin(HttpSession session) {
		return (LoginAdmin) session.getAttribute(AdminAuthController.ADMIN_KEY);
	}
}
