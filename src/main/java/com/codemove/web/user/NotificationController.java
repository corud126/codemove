package com.codemove.web.user;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.member.LoginMember;
import com.codemove.notification.NotificationService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 알림 읽음 처리 (G-H13, more.js). 목록 화면은 MoreController(/notifications).
 * /notifications/** 는 로그인 검사 경로(WebConfig)에 없어서 여기서 직접 확인한다.
 */
@Controller
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	/** 행 클릭: 읽음 → 남은 안 읽은 수 */
	@PostMapping("/notifications/{id}/read")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> read(@PathVariable Long id, HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		if (member == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요해요."));
		}
		if (!notificationService.markRead(member.memberId(), id)) {
			return ResponseEntity.badRequest().body(Map.of("message", "알림을 찾을 수 없어요."));
		}
		return ResponseEntity.ok(Map.of("unread", notificationService.unreadCount(member.memberId())));
	}

	/** 모두 읽음 */
	@PostMapping("/notifications/read-all")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> readAll(HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		if (member == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요해요."));
		}
		notificationService.markAllRead(member.memberId());
		return ResponseEntity.ok(Map.of("unread", 0));
	}
}
