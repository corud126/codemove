package com.codemove.web;

import org.springframework.web.servlet.HandlerInterceptor;

import com.codemove.web.admin.AdminAuthController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 관리자 화면 접근 확인: 로그인하지 않았으면 /admin/login,
 * 로그인했지만 초기 비밀번호 상태면 /admin/password 로 보낸다.
 */
public class AdminLoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		HttpSession session = request.getSession(false);
		String contextPath = request.getContextPath();
		if (session == null || session.getAttribute(AdminAuthController.ADMIN_KEY) == null) {
			response.sendRedirect(contextPath + "/admin/login");
			return false;
		}
		boolean onPasswordPage = request.getRequestURI().equals(contextPath + "/admin/password");
		if (!Boolean.TRUE.equals(session.getAttribute(AdminAuthController.PW_CHANGED_KEY)) && !onPasswordPage) {
			response.sendRedirect(contextPath + "/admin/password");
			return false;
		}
		return true;
	}
}
