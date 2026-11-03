package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 회원 (TBL_MEMBER) */
@Getter
@Setter
public class Member {

	private Long memberId;
	private String memberName;
	/** 010-0000-0000 형식으로 저장 (휴대전화 로그인 식별자, UNIQUE) */
	private String phone;
	private String isVerified;
	private String isActive;
	private LocalDateTime joinedAt;
	private LocalDateTime termsAgreedAt;
	private LocalDateTime withdrawnAt;
}
