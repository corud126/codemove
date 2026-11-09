package com.codemove.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 챗봇 '화면 이동' 버튼의 화면 이름 ↔ 사용자 앱 주소.
 * 관리자 답변 관리 화면은 이름으로 고르고, TBL_CHATBOT_ACTION.ACTION_VALUE 에는 주소를 저장한다.
 */
public final class ChatScreens {

	/** 관리자 화면의 '이동할 화면' 목록 순서 그대로 */
	public static final Map<String, String> URLS = new LinkedHashMap<>();

	static {
		URLS.put("견적 신청", "/quote");
		URLS.put("내 이사", "/move");
		URLS.put("예약 상세", "/move?tab=booking");
		URLS.put("견적 상세", "/move?tab=estimate");
		URLS.put("견적서", "/more/estimates");
		URLS.put("알림", "/notifications");
	}

	private ChatScreens() {
	}

	/** 화면 이름 → 주소 (모르는 이름이면 그대로: 관리자가 주소를 직접 넣은 경우) */
	public static String toUrl(String name) {
		return name == null ? null : URLS.getOrDefault(name, name);
	}

	/** 주소 → 화면 이름 (목록에 없는 주소면 그대로) */
	public static String toName(String url) {
		if (url == null) {
			return null;
		}
		return URLS.entrySet().stream().filter(e -> e.getValue().equals(url)).map(Map.Entry::getKey).findFirst()
				.orElse(url);
	}

	/** 고객 앱 안 주소만 허용 (외부 링크 · javascript: 방지) */
	public static boolean isSafeUrl(String url) {
		return url != null && url.startsWith("/") && !url.startsWith("//");
	}
}
