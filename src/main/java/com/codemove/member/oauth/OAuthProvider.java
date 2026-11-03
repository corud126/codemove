package com.codemove.member.oauth;

import java.util.Arrays;
import java.util.Optional;

/** 간편 로그인 제공자. code 는 공통코드 LOGIN_TYPE (애플 03 은 미연동) */
public enum OAuthProvider {

	KAKAO("kakao", "01",
			"https://kauth.kakao.com/oauth/authorize",
			"https://kauth.kakao.com/oauth/token",
			"https://kapi.kakao.com/v2/user/me"),
	NAVER("naver", "02",
			"https://nid.naver.com/oauth2.0/authorize",
			"https://nid.naver.com/oauth2.0/token",
			"https://openapi.naver.com/v1/nid/me");

	private final String path;
	private final String loginType;
	private final String authorizeUrl;
	private final String tokenUrl;
	private final String userUrl;

	OAuthProvider(String path, String loginType, String authorizeUrl, String tokenUrl, String userUrl) {
		this.path = path;
		this.loginType = loginType;
		this.authorizeUrl = authorizeUrl;
		this.tokenUrl = tokenUrl;
		this.userUrl = userUrl;
	}

	/** URL 경로(/login/oauth/kakao)의 이름으로 찾는다 */
	public static Optional<OAuthProvider> fromPath(String path) {
		return Arrays.stream(values()).filter(p -> p.path.equals(path)).findFirst();
	}

	public String path() {
		return path;
	}

	public String loginType() {
		return loginType;
	}

	String authorizeUrl() {
		return authorizeUrl;
	}

	String tokenUrl() {
		return tokenUrl;
	}

	String userUrl() {
		return userUrl;
	}
}
