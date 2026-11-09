package com.codemove.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.Admin;

@Mapper
public interface AdminMapper {

	Admin selectByLoginId(String loginId);

	Admin selectById(Long adminId);

	/** 비밀번호를 바꾸고 PW_CHANGED_AT 을 기록한다 */
	int updatePassword(@Param("adminId") Long adminId, @Param("password") String password);
}
