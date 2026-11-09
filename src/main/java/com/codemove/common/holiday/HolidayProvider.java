package com.codemove.common.holiday;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 공휴일 · 손 없는 날 (DB 저장 없음, Q17).
 * 공휴일: 서비스 키(codemove.holiday.service-key)가 있으면 공공데이터포털 특일 정보 getRestDeInfo 를 달마다 한 번 불러 기억해 둔다.
 * 키가 없거나 API 가 실패하면 내장 목록(2026 · 2027, 그 밖의 해는 양력 고정 공휴일만)을 쓴다.
 * 손 없는 날: 음력 9 · 0일 (LunarDay 계산). 표시만 하고 휴무가 아니다.
 */
@Component
@EnableConfigurationProperties(HolidayProperties.class)
public class HolidayProvider {

	private static final Logger log = LoggerFactory.getLogger(HolidayProvider.class);

	private static final String API_URL = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};
	/** API 실패 시 내장 목록을 이 시간 동안 쓰고 다시 시도 */
	private static final Duration RETRY_AFTER = Duration.ofMinutes(10);

	/** 양력 고정 공휴일 */
	private static final Map<MonthDay, String> FIXED = new LinkedHashMap<>();
	/** 해마다 다른 공휴일 (설날 · 부처님오신날 · 추석 · 대체공휴일 · 선거일) */
	private static final Map<LocalDate, String> YEARLY = new LinkedHashMap<>();

	static {
		FIXED.put(MonthDay.of(1, 1), "1월1일");
		FIXED.put(MonthDay.of(3, 1), "삼일절");
		FIXED.put(MonthDay.of(5, 5), "어린이날");
		FIXED.put(MonthDay.of(6, 6), "현충일");
		FIXED.put(MonthDay.of(8, 15), "광복절");
		FIXED.put(MonthDay.of(10, 3), "개천절");
		FIXED.put(MonthDay.of(10, 9), "한글날");
		FIXED.put(MonthDay.of(12, 25), "기독탄신일");
		// 2026
		yearly("2026-02-16", "설날");
		yearly("2026-02-17", "설날");
		yearly("2026-02-18", "설날");
		yearly("2026-03-02", "대체공휴일");
		yearly("2026-05-24", "부처님오신날");
		yearly("2026-05-25", "대체공휴일");
		yearly("2026-06-03", "전국동시지방선거");
		yearly("2026-08-17", "대체공휴일");
		yearly("2026-09-24", "추석");
		yearly("2026-09-25", "추석");
		yearly("2026-09-26", "추석");
		yearly("2026-10-05", "대체공휴일");
		// 2027
		yearly("2027-02-06", "설날");
		yearly("2027-02-07", "설날");
		yearly("2027-02-08", "설날");
		yearly("2027-02-09", "대체공휴일");
		yearly("2027-05-13", "부처님오신날");
		yearly("2027-08-16", "대체공휴일");
		yearly("2027-09-14", "추석");
		yearly("2027-09-15", "추석");
		yearly("2027-09-16", "추석");
		yearly("2027-10-04", "대체공휴일");
		yearly("2027-10-11", "대체공휴일");
		yearly("2027-12-27", "대체공휴일");
	}

	private static void yearly(String date, String name) {
		YEARLY.put(LocalDate.parse(date), name);
	}

	private record Cached(List<Holiday> holidays, Instant expiresAt) {
	}

	private final HolidayProperties properties;
	private final RestClient restClient;
	private final Map<YearMonth, Cached> cache = new ConcurrentHashMap<>();

	public HolidayProvider(HolidayProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	/** 그 달 공휴일 (날짜 순) */
	public List<Holiday> holidays(YearMonth month) {
		if (!properties.hasKey()) {
			return builtIn(month);
		}
		Cached cached = cache.get(month);
		if (cached != null && (cached.expiresAt() == null || Instant.now().isBefore(cached.expiresAt()))) {
			return cached.holidays();
		}
		try {
			List<Holiday> fetched = fetch(month);
			cache.put(month, new Cached(fetched, null));
			return fetched;
		} catch (RuntimeException e) {
			log.warn("특일 정보 API 조회 실패({}), 내장 공휴일 목록 사용: {}", month, e.toString());
			List<Holiday> fallback = builtIn(month);
			cache.put(month, new Cached(fallback, Instant.now().plus(RETRY_AFTER)));
			return fallback;
		}
	}

	/** 공휴일인지 */
	public boolean isHoliday(LocalDate date) {
		return holidays(YearMonth.from(date)).stream().anyMatch(h -> h.date().equals(date));
	}

	/** 그 달 손 없는 날 (음력 9 · 10 · 19 · 20 · 29 · 30일) */
	public List<LocalDate> noMoveDays(YearMonth month) {
		List<LocalDate> days = new ArrayList<>();
		for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
			if (LunarDay.isNoMoveDay(d)) {
				days.add(d);
			}
		}
		return days;
	}

	/** 내장 목록 (API 키가 없을 때) */
	static List<Holiday> builtIn(YearMonth month) {
		Map<LocalDate, String> found = new LinkedHashMap<>();
		FIXED.forEach((md, name) -> {
			if (md.getMonthValue() == month.getMonthValue()) {
				found.put(md.atYear(month.getYear()), name);
			}
		});
		YEARLY.forEach((date, name) -> {
			if (YearMonth.from(date).equals(month)) {
				found.putIfAbsent(date, name);
			}
		});
		return found.entrySet().stream().map(e -> new Holiday(e.getKey(), e.getValue()))
				.sorted(Comparator.comparing(Holiday::date)).toList();
	}

	/** getRestDeInfo 호출. 응답 items.item 은 0건이면 빈 문자열, 1건이면 객체, 여러 건이면 배열 */
	private List<Holiday> fetch(YearMonth month) {
		String key = properties.serviceKey();
		// 포털의 '인코딩' 키(% 포함)는 그대로, '디코딩' 키는 인코딩해서 보낸다
		String encodedKey = key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
		URI uri = URI.create(API_URL + "?serviceKey=" + encodedKey + "&solYear=" + month.getYear()
				+ "&solMonth=" + String.format("%02d", month.getMonthValue()) + "&numOfRows=50&_type=json");
		Map<String, Object> body = restClient.get().uri(uri).retrieve().body(JSON);
		Object response = body != null ? body.get("response") : null;
		if (!(response instanceof Map<?, ?> res) || !(res.get("body") instanceof Map<?, ?> b)) {
			throw new IllegalStateException("응답 형식 오류: " + body);
		}
		List<Object> items = new ArrayList<>();
		if (b.get("items") instanceof Map<?, ?> wrapper) {
			Object item = wrapper.get("item");
			if (item instanceof List<?> list) {
				items.addAll(list);
			} else if (item != null) {
				items.add(item);
			}
		}
		Map<LocalDate, String> found = new LinkedHashMap<>();
		DateTimeFormatter ymd = DateTimeFormatter.BASIC_ISO_DATE;
		for (Object o : items) {
			if (o instanceof Map<?, ?> item && !"N".equals(String.valueOf(item.get("isHoliday")))) {
				LocalDate date = LocalDate.parse(String.valueOf(item.get("locdate")), ymd);
				found.putIfAbsent(date, String.valueOf(item.get("dateName")));
			}
		}
		return found.entrySet().stream().map(e -> new Holiday(e.getKey(), e.getValue()))
				.sorted(Comparator.comparing(Holiday::date)).toList();
	}
}
