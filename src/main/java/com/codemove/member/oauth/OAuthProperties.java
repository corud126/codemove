package com.codemove.member.oauth;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 간편 로그인 앱 키 (application-local.yml 의 codemove.oauth.kakao / naver).
 * client-id 가 비어 있으면 해당 간편 로그인은 꺼진 상태로 본다.
 */
@ConfigurationProperties("codemove.oauth")
public record OAuthProperties(Map<String, Client> providers) {

	public OAuthProperties {
		providers = providers != null ? providers : Map.of();
	}

	public record Client(String clientId, String clientSecret) {
	}

	/** 키가 설정된 제공자의 앱 정보. 없으면 null */
	public Client client(OAuthProvider provider) {
		Client client = providers.get(provider.path());
		return client != null && client.clientId() != null && !client.clientId().isBlank() ? client : null;
	}
}
