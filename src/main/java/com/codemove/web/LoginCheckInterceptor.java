package com.codemove.web;

import org.springframework.web.servlet.HandlerInterceptor;

import com.codemove.web.user.HomeController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 비회원 접근 차단: 로그인하지 않았으면 로그인 방법 선택(/login)으로 보낸다.
 * 로그인 여부는 세션의 로그인 회원(HomeController.LOGIN_KEY)으로 판단한다.
 */
public class LoginCheckInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute(HomeController.LOGIN_KEY) != null) {
			return true;
		}
		response.sendRedirect(request.getContextPath() + "/login");
		return false;
	}
}
