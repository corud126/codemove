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
 * 주소 검색: 행정안전부 도로명주소 검색 API (business.juso.go.kr, 승인키 codemove.juso.confm-key).
 * 승인키가 없으면 개발용 예시 주소에서 찾는다.
 */
@Slf4j
@Component
public class AddressSearch {

	private static final String API = "https://business.juso.go.kr/addrlink/addrLinkApi.do";
	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};
	private static final List<Address> SAMPLES = List.of(
			new Address("서울 강남구 테헤란로 123", "서울 강남구 역삼동 123-45"),
			new Address("서울 강남구 테헤란로 152", "서울 강남구 역삼동 678-9"),
			new Address("서울 강남구 강남대로 396", "서울 강남구 역삼동 831-2"),
			new Address("서울 서초구 서초대로 456", "서울 서초구 서초동 1321-5"),
			new Address("경기 성남시 분당구 판교역로 235", "경기 성남시 분당구 삼평동 681"),
			new Address("경기 수원시 영통구 광교중앙로 145", "경기 수원시 영통구 이의동 1336"));

	public record Address(String road, String jibun) {
	}

	private final String confmKey;
	private final RestClient restClient = RestClient.create();

	public AddressSearch(@Value("${codemove.juso.confm-key:}") String confmKey) {
		this.confmKey = confmKey;
	}

	/** 최대 10건. 검색어가 2글자 미만이면 빈 목록 */
	public List<Address> search(String keyword) {
		String q = keyword == null ? "" : keyword.trim();
		if (q.length() < 2) {
			return List.of();
		}
		if (confmKey.isBlank()) {
			return SAMPLES.stream().filter(a -> a.road().contains(q) || a.jibun().contains(q)).toList();
		}
		try {
			Map<String, Object> body = restClient.get().uri(UriComponentsBuilder.fromUriString(API)
					.queryParam("confmKey", confmKey).queryParam("currentPage", 1).queryParam("countPerPage", 10)
					.queryParam("keyword", q).queryParam("resultType", "json").encode().build().toUri())
					.retrieve().body(JSON);
			Object juso = body != null && body.get("results") instanceof Map<?, ?> r ? r.get("juso") : null;
			if (!(juso instanceof List<?> list)) {
				return List.of();
			}
			return list.stream().filter(Map.class::isInstance).map(Map.class::cast)
					.map(m -> new Address(String.valueOf(m.get("roadAddr")), String.valueOf(m.get("jibunAddr"))))
					.toList();
		} catch (RestClientException e) {
			log.warn("주소 검색 실패: {}", q, e);
			return List.of();
		}
	}
}
