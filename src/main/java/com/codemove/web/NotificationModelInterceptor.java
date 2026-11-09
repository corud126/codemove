package com.codemove.web;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.codemove.member.LoginMember;
import com.codemove.notification.NotificationService;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 화면(뷰)을 그릴 때 공통 알림 값을 모델에 넣는다. JSON 응답 · redirect 에는 넣지 않는다.
 * - 사용자 화면 + 로그인 회원: unreadNotifications (헤더 종의 안 읽은 알림 수, fragments/user-layout :: header)
 * - 관리자 화면 + 로그인 관리자: adminNotificationPanel (알림 패널 A-019, fragments/admin-overlays)
 * 조회가 실패해도 화면은 그대로 그린다 (템플릿은 값이 없으면 기본값 · 예시 사용).
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationModelInterceptor implements HandlerInterceptor {

	public static final String UNREAD = "unreadNotifications";
	public static final String ADMIN_PANEL = "adminNotificationPanel";

	private final NotificationService notificationService;

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav) {
		if (mav == null || mav.getViewName() == null || mav.getViewName().startsWith("redirect:")
				|| mav.getViewName().startsWith("forward:")) {
			return;
		}
		HttpSession session = request.getSession(false);
		if (session == null) {
			return;
		}
		String path = request.getRequestURI().substring(request.getContextPath().length());
		try {
			if (path.equals("/admin") || path.startsWith("/admin/")) {
				if (session.getAttribute(AdminAuthController.ADMIN_KEY) != null && !mav.getModel().containsKey(ADMIN_PANEL)) {
					mav.addObject(ADMIN_PANEL, notificationService.adminPanel());
				}
			} else if (session.getAttribute(HomeController.LOGIN_KEY) instanceof LoginMember m
					&& !mav.getModel().containsKey(UNREAD)) {
				mav.addObject(UNREAD, notificationService.unreadCount(m.memberId()));
			}
		} catch (RuntimeException e) {
			log.warn("알림 값 조회 실패 {}", path, e);
		}
	}
}
