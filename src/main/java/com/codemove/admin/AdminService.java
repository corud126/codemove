package com.codemove.admin;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.Admin;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

	private final AdminMapper adminMapper;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	/** 아이디·비밀번호가 맞는 사용 중 계정. 아니면 null */
	public Admin login(String loginId, String password) {
		Admin admin = adminMapper.selectByLoginId(loginId);
		if (admin == null || !"Y".equals(admin.getIsActive()) || !passwordEncoder.matches(password, admin.getPassword())) {
			return null;
		}
		return admin;
	}

	/** 비밀번호 조건: 8자 이상 · 영문·숫자 포함 (비밀번호 변경 화면과 같음) */
	public static boolean isValidPassword(String password) {
		return password != null && password.length() >= 8
				&& password.matches(".*[A-Za-z].*") && password.matches(".*\\d.*");
	}

	/** 현재 비밀번호가 맞고 새 비밀번호가 조건을 만족하면 변경. 성공 여부를 돌려준다 */
	@Transactional
	public boolean changePassword(Long adminId, String current, String newPassword) {
		Admin admin = adminMapper.selectById(adminId);
		if (admin == null || !passwordEncoder.matches(current, admin.getPassword())
				|| !isValidPassword(newPassword) || newPassword.equals(current)) {
			return false;
		}
		adminMapper.updatePassword(adminId, passwordEncoder.encode(newPassword));
		return true;
	}
}
