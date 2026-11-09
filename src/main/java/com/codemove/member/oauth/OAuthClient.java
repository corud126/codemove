package com.codemove.member.oauth;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.codemove.member.SocialAccount;

/**
 * 카카오·네이버 OAuth 2.0 인가 코드 방식.
 * 로그인 화면 주소 만들기 → (사용자 동의) → 콜백의 code 로 토큰 발급 → 회원 식별값 조회.
 * 이름·번호는 받지 않는다: 처음 들어온 소셜 계정은 회원가입 본인인증을 거쳐 회원과 연결한다.
 */
@Component
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthClient {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};

	private final OAuthProperties properties;
	private final RestClient restClient = RestClient.create();

	public OAuthClient(OAuthProperties properties) {
		this.properties = properties;
	}

	public boolean isEnabled(OAuthProvider provider) {
		return properties.client(provider) != null;
	}

	/** 제공자 로그인 화면 주소 */
	public String authorizeUrl(OAuthProvider provider, String redirectUri, String state) {
		return UriComponentsBuilder.fromUriString(provider.authorizeUrl())
				.queryParam("response_type", "code")
				.queryParam("client_id", properties.client(provider).clientId())
				.queryParam("redirect_uri", redirectUri)
				.queryParam("state", state)
				.encode().build().toUriString();
	}

	/** 콜백 code 로 소셜 계정 식별값을 얻는다. 실패하면 RestClientException / IllegalStateException */
	public SocialAccount fetchAccount(OAuthProvider provider, String code, String redirectUri, String state) {
		OAuthProperties.Client client = properties.client(provider);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", client.clientId());
		if (client.clientSecret() != null && !client.clientSecret().isBlank()) {
			form.add("client_secret", client.clientSecret());
		}
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		form.add("state", state);

		Map<String, Object> token = restClient.post().uri(provider.tokenUrl())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
				.retrieve().body(JSON);
		Object accessToken = token != null ? token.get("access_token") : null;
		if (accessToken == null) {
			throw new IllegalStateException(provider + " 토큰 발급 실패: " + token);
		}

		Map<String, Object> user = restClient.get().uri(provider.userUrl())
				.header("Authorization", "Bearer " + accessToken)
				.retrieve().body(JSON);
		// 카카오: { id }, 네이버: { response: { id } }
		Object id = switch (provider) {
			case KAKAO -> user != null ? user.get("id") : null;
			case NAVER -> user != null && user.get("response") instanceof Map<?, ?> r ? r.get("id") : null;
		};
		if (id == null) {
			throw new IllegalStateException(provider + " 회원 정보 조회 실패: " + user);
		}
		return new SocialAccount(provider.loginType(), id.toString());
	}
}
