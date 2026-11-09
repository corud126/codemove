package com.codemove.admin;

import java.io.Serializable;

import com.codemove.domain.Admin;

/** 세션에 두는 로그인 관리자 정보 (AdminAuthController.ADMIN_KEY) */
public record LoginAdmin(Long adminId, String loginId, String name, String role) implements Serializable {

	/** ADMIN_ROLE 01 */
	public static final String ROLE_MASTER = "01";

	public static LoginAdmin of(Admin admin) {
		return new LoginAdmin(admin.getAdminId(), admin.getLoginId(), admin.getAdminName(), admin.getAdminRole());
	}

	/** 마스터만 할 수 있는 기능(운영자 관리 — 추후)에 사용 */
	public boolean isMaster() {
		return ROLE_MASTER.equals(role);
	}
}
