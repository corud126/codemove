package com.codemove.common.address;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * 주소 검색: 카카오 로컬 API (REST API 키 codemove.kakao.rest-api-key, 비어 있으면 카카오 로그인 client-id).
 * 주소 검색 결과가 없으면 장소(키워드) 검색으로 한 번 더 찾는다 (건물 · 아파트 이름 입력).
 * 좌표(lng · lat)는 이후 거리 계산에 쓴다. 키가 없으면 개발용 예시 주소에서 찾는다.
 */
@Slf4j
@Component
public class AddressSearch {

	private static final String ADDRESS_API = "https://dapi.kakao.com/v2/local/search/address.json";
	private static final String KEYWORD_API = "https://dapi.kakao.com/v2/local/search/keyword.json";
	private static final int SIZE = 10;
	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};
	private static final List<Address> SAMPLES = List.of(
			new Address("서울 강남구 테헤란로 123", "서울 강남구 역삼동 123-45", null, null),
			new Address("서울 강남구 테헤란로 152", "서울 강남구 역삼동 678-9", null, null),
			new Address("서울 강남구 강남대로 396", "서울 강남구 역삼동 831-2", null, null),
			new Address("서울 서초구 서초대로 456", "서울 서초구 서초동 1321-5", null, null),
			new Address("경기 성남시 분당구 판교역로 235", "경기 성남시 분당구 삼평동 681", null, null),
			new Address("경기 수원시 영통구 광교중앙로 145", "경기 수원시 영통구 이의동 1336", null, null));

	/** road: 도로명 주소 (없으면 지번 주소) · jibun: 지번 주소 · lng/lat: 경도/위도 (예시 주소는 null) */
	public record Address(String road, String jibun, Double lng, Double lat) {
	}

	private final String restApiKey;
	private final RestClient restClient = RestClient.create();

	public AddressSearch(@Value("${codemove.kakao.rest-api-key:}") String restApiKey,
			@Value("${codemove.oauth.providers.kakao.client-id:}") String kakaoLoginKey) {
		String key = restApiKey == null || restApiKey.isBlank() ? kakaoLoginKey : restApiKey;
		this.restApiKey = key == null ? "" : key.trim();
	}

	/** 최대 10건. 검색어가 2글자 미만이면 빈 목록 */
	public List<Address> search(String keyword) {
		String q = keyword == null ? "" : keyword.trim();
		if (q.length() < 2) {
			return List.of();
		}
		if (restApiKey.isBlank()) {
			return SAMPLES.stream().filter(a -> a.road().contains(q) || a.jibun().contains(q)).toList();
		}
		try {
			List<Address> found = documents(ADDRESS_API, q).stream().map(AddressSearch::fromAddress).toList();
			if (!found.isEmpty()) {
				return found;
			}
			return documents(KEYWORD_API, q).stream().map(AddressSearch::fromPlace).toList();
		} catch (RestClientException e) {
			log.warn("주소 검색 실패: {}", q, e);
			return List.of();
		}
	}

	private List<Map<?, ?>> documents(String api, String q) {
		Map<String, Object> body = restClient.get()
				.uri(UriComponentsBuilder.fromUriString(api).queryParam("query", q).queryParam("size", SIZE).encode()
						.build().toUri())
				.header("Authorization", "KakaoAK " + restApiKey).retrieve().body(JSON);
		Object docs = body != null ? body.get("documents") : null;
		if (!(docs instanceof List<?> list)) {
			return List.of();
		}
		return list.stream().filter(Map.class::isInstance).<Map<?, ?>>map(Map.class::cast).toList();
	}

	/** 주소 검색 결과: road_address · address 는 각각 없을 수 있다 */
	private static Address fromAddress(Map<?, ?> doc) {
		String road = text(doc.get("road_address") instanceof Map<?, ?> r ? r.get("address_name") : null);
		String jibun = text(doc.get("address") instanceof Map<?, ?> a ? a.get("address_name") : null);
		if (jibun.isEmpty()) {
			jibun = text(doc.get("address_name"));
		}
		return new Address(road.isEmpty() ? jibun : road, jibun, number(doc.get("x")), number(doc.get("y")));
	}

	/** 장소 검색 결과: 도로명 · 지번 주소 이름만 쓴다 (도로명이 없으면 지번으로 채운다) */
	private static Address fromPlace(Map<?, ?> doc) {
		String road = text(doc.get("road_address_name"));
		String jibun = text(doc.get("address_name"));
		return new Address(road.isEmpty() ? jibun : road, jibun, number(doc.get("x")), number(doc.get("y")));
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static Double number(Object value) {
		try {
			return value == null ? null : Double.valueOf(String.valueOf(value));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
