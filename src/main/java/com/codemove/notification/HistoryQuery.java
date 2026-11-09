package com.codemove.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 발송 내역 검색 조건 (SCR-A-013). 모두 서버 쿼리 조건으로 처리한다.
 * - 검색어: 날짜 모양이면 날짜 검색(30일 범위를 넘어 조회), 아니면 고객 이름 포함 검색
 * - channel: NTF_CHANNEL 코드 · result: held(보류) | sms(문자 대체) | sent(발송)
 * - 날짜 검색 규칙은 화면(견적 관리 estimate.js · notification.js)과 같다: 26.3 · 3.25 · 3월 · 260325 · 26.11.02
 *   → 작성일 TO_CHAR(CREATED_AT, 'YYMMDD') 에 대한 LIKE 패턴 목록(datePatterns)으로 바꾼다
 *
 * @param name         고객 이름 검색어 (날짜 검색이면 null)
 * @param datePatterns 날짜 검색 LIKE 패턴 (날짜 검색이 아니면 null)
 * @param channel      NTF_CHANNEL (없으면 null)
 * @param result       held | sms | sent (없으면 null)
 * @param recentDays   기본 범위 최근 N일 (날짜 검색이면 0 = 제한 없음)
 */
public record HistoryQuery(String name, List<String> datePatterns, String channel, String result, int recentDays) {

	/** 매칭되는 날짜가 없을 때 넣는 패턴 (6자리 숫자와 절대 맞지 않음) */
	private static final String NONE = "-";

	public static HistoryQuery of(String query, String channel, String result, int recentDays) {
		String q = query == null ? "" : query.strip();
		String ch = blankToNull(channel);
		String res = "held".equals(result) || "sms".equals(result) || "sent".equals(result) ? result : null;
		if (q.isEmpty()) {
			return new HistoryQuery(null, null, ch, res, recentDays);
		}
		List<String> patterns = datePatterns(q);
		if (patterns == null) {
			return new HistoryQuery(q.length() > 50 ? q.substring(0, 50) : q, null, ch, res, recentDays);
		}
		if (patterns.isEmpty()) {
			patterns = List.of(NONE);
		}
		return new HistoryQuery(null, patterns, ch, res, 0);
	}

	/** 날짜 모양이 아니면 null, 날짜 모양이면 YYMMDD LIKE 패턴 목록 */
	static List<String> datePatterns(String q) {
		String text = q.replaceAll("년|월", ".").replace("일", "").strip();
		if (!text.matches("^[\\d.\\s/-]+$")) {
			return null;
		}
		List<String> out = new ArrayList<>();
		if (!text.matches(".*[.\\s/-].*")) {
			// 숫자만: YYMMDD 앞부분 · YYYY 앞부분 · MMDD 앞부분 · 한 자리 월
			out.add(text + "%");
			if ("2".equals(text) || "20".equals(text)) {
				out.add("%");
			} else if (text.startsWith("20")) {
				out.add(text.substring(2) + "%");
			}
			out.add("__" + text + "%");
			if (text.length() == 1) {
				out.add("__0" + text + "%");
			}
			return out;
		}
		String[] parts = java.util.Arrays.stream(text.split("[.\\s/-]+")).filter(s -> !s.isEmpty()).toArray(String[]::new);
		if (parts.length == 0) {
			return out;
		}
		boolean typing = !text.matches(".*[.\\s/-]$");
		List<String[]> forms = new ArrayList<>();
		if (q.contains("년")) {
			forms.add(new String[] { "y", "m", "d" });
		} else if (q.contains("월")) {
			forms.add(new String[] { "m", "d" });
		} else {
			forms.add(new String[] { "y", "m", "d" });
			forms.add(new String[] { "m", "d" });
		}
		for (String[] form : forms) {
			if (parts.length > form.length) {
				continue;
			}
			List<String> combos = List.of(form.length == 2 ? "__" : "");
			for (int i = 0; i < form.length; i++) {
				List<String> options = i < parts.length
						? fieldOptions(form[i], parts[i], i == parts.length - 1 && typing)
						: List.of("__");
				List<String> next = new ArrayList<>();
				for (String c : combos) {
					for (String o : options) {
						next.add(c + o);
					}
				}
				combos = next;
			}
			combos.stream().filter(c -> !out.contains(c)).forEach(out::add);
		}
		return out;
	}

	/** 한 칸(연·월·일)에 맞는 두 자리 LIKE 패턴 */
	private static List<String> fieldOptions(String field, String p, boolean lastTyping) {
		if ("y".equals(field) && p.length() > 2) {
			if (p.length() == 4) {
				return List.of(p.substring(2));
			}
			return p.length() == 3 && p.startsWith("20") ? List.of(p.charAt(2) + "_") : List.of();
		}
		if (lastTyping && p.length() == 1) {
			return List.of("0" + p, p + "_");
		}
		return p.length() <= 2 ? List.of(p.length() == 1 ? "0" + p : p) : List.of();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
