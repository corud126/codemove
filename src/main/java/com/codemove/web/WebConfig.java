package com.codemove.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	/** 로그인한 회원만 들어갈 수 있는 사용자 화면 (비회원은 홈 ?state=guest 만 가능) */
	private static final String[] MEMBER_ONLY = {
			"/move", "/quote", "/quote/**", "/chat", "/more", "/more/**", "/notifications",
			"/estimates/**", "/reservations/**"
	};

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new LoginCheckInterceptor()).addPathPatterns(MEMBER_ONLY);
	}
}
