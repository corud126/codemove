package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 간편 로그인 연결 (TBL_SOCIAL) */
@Getter
@Setter
public class Social {

	private Long socialId;
	private Long memberId;
	/** LOGIN_TYPE: 01 카카오 · 02 네이버 · 03 애플 */
	private String loginType;
	/** 소셜 서비스의 회원 식별값 */
	private String socialNo;
	private LocalDateTime connectedAt;
}
