package com.codemove.member;

import java.io.Serializable;

/** 간편 로그인으로 확인한 소셜 계정 (LOGIN_TYPE 코드 + 소셜 서비스 회원 식별값) */
public record SocialAccount(String loginType, String socialNo) implements Serializable {
}
