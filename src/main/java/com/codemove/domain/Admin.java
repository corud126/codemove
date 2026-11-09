package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 관리자 (TBL_ADMIN) */
@Getter
@Setter
public class Admin {

	private Long adminId;
	/** 사번·학번 */
	private String loginId;
	/** BCrypt 해시 */
	private String password;
	private String adminName;
	private String phone;
	/** ADMIN_ROLE: 01 마스터 · 02 운영자 */
	private String adminRole;
	/** NULL 이면 초기 비밀번호 상태 → 첫 로그인 시 변경 강제 */
	private LocalDateTime pwChangedAt;
	private String isActive;
	private String pushEnabled;
	private LocalDateTime createdAt;
}
