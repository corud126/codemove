package com.codemove.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.codemove.notification.NotificationService;

import lombok.RequiredArgsConstructor;

/** 알림 공통 모델 값 (NotificationModelInterceptor). 로그인 검사(WebConfig)와 따로 등록한다 */
@Configuration
@RequiredArgsConstructor
public class NotificationWebConfig implements WebMvcConfigurer {

	private final NotificationService notificationService;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new NotificationModelInterceptor(notificationService))
				.addPathPatterns("/**")
				.excludePathPatterns("/css/**", "/js/**", "/images/**", "/fonts/**", "/dev/**");
	}
}
