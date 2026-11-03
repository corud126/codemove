package com.codemove.member;

import java.io.Serializable;

import com.codemove.domain.Member;

/** 세션에 두는 로그인 회원 정보 (HomeController.LOGIN_KEY) */
public record LoginMember(Long memberId, String name, String phone) implements Serializable {

	public static LoginMember of(Member member) {
		return new LoginMember(member.getMemberId(), member.getMemberName(), member.getPhone());
	}
}
