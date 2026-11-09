package com.codemove.notification;

import java.util.Arrays;

/**
 * 알림 세부 종류 (공통코드 NTF_DETAIL) → 분류(NTF_TYPE) · 아이콘 · 중요 표시 (ERD Q8: 분류·아이콘 매핑은 애플리케이션 enum).
 * 화면 이름은 공통코드에서 읽는다 (관리자: CODE_NAME, 고객: USER_NAME).
 * userIcon: 사용자 알림 화면 아이콘 (schedule · estimate · payment · default)
 * adminIcon: 관리자 알림 패널 아이콘 (images/admin/icons-v2.svg 의 i-*)
 */
public enum NotificationDetail {

	/** 견적 접수 — 회원·관리자 */
	QUOTE_RECEIVED("01", NotificationDetail.MY_MOVE, "estimate", "document", false),
	/** 견적 도착 (승인) — 회원 */
	QUOTE_ARRIVED("02", NotificationDetail.MY_MOVE, "estimate", "document", false),
	/** 견적 변경 (승인 후 수정) — 회원 */
	QUOTE_CHANGED("03", NotificationDetail.MY_MOVE, "estimate", "edit", false),
	/** 예약 확정 — 회원·관리자 */
	RESERVATION_CONFIRMED("04", NotificationDetail.MY_MOVE, "schedule", "calendarCheck", false),
	/** 예약 취소 — 회원·관리자 */
	RESERVATION_CANCELLED("05", NotificationDetail.MY_MOVE, "schedule", "x", false),
	/** 일정 변경 — 회원 */
	SCHEDULE_CHANGED("06", NotificationDetail.MY_MOVE, "schedule", "calendar", false),
	/** 이사 임박 D-3 — 회원 (NotificationScheduler) */
	MOVE_D3("07", NotificationDetail.MY_MOVE, "schedule", "clock", false),
	/** 오늘의 일정 (이사 당일) — 회원·관리자 */
	MOVE_TODAY("08", NotificationDetail.MY_MOVE, "schedule", "truck", false),
	/** 이사 완료 — 회원 */
	MOVE_COMPLETED("09", NotificationDetail.MY_MOVE, "schedule", "circleCheck", false),
	/** 계약금 결제 완료 — 회원 */
	DEPOSIT_PAID("10", NotificationDetail.MY_MOVE, "payment", "card", false),
	/** 채팅 상담 대기 — 관리자 (중요) */
	CHAT_WAITING("13", NotificationDetail.ADMIN_WORK, "default", "chat", true),
	/** 신규 가입 — 관리자 */
	MEMBER_JOINED("14", NotificationDetail.ADMIN_WORK, "default", "user", false),
	/** 상담 답변 도착 — 회원 */
	CHAT_REPLY("15", NotificationDetail.MY_MOVE, "default", "chat", false);

	/** NTF_TYPE 01 내 이사 (사용자 알림 '내 이사' 탭) */
	public static final String MY_MOVE = "01";
	/** NTF_TYPE 03 관리자 업무 (사용자 화면에는 나오지 않음) */
	public static final String ADMIN_WORK = "03";

	private final String code;
	private final String type;
	private final String userIcon;
	private final String adminIcon;
	private final boolean important;

	NotificationDetail(String code, String type, String userIcon, String adminIcon, boolean important) {
		this.code = code;
		this.type = type;
		this.userIcon = userIcon;
		this.adminIcon = adminIcon;
		this.important = important;
	}

	public String code() {
		return code;
	}

	public String type() {
		return type;
	}

	public String userIcon() {
		return userIcon;
	}

	public String adminIcon() {
		return adminIcon;
	}

	public boolean important() {
		return important;
	}

	/** 회원에게 보낼 수 있는 종류인지 (관리자 업무 분류는 회원 화면에 없음) */
	public boolean forMember() {
		return MY_MOVE.equals(type);
	}

	/** 코드로 찾기. 없는 코드면 IllegalArgumentException */
	public static NotificationDetail of(String code) {
		return Arrays.stream(values()).filter(d -> d.code.equals(code)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("알 수 없는 알림 종류: " + code));
	}

	/** 코드로 찾기. 없는 코드면 null (화면 표시용) */
	public static NotificationDetail find(String code) {
		return Arrays.stream(values()).filter(d -> d.code.equals(code)).findFirst().orElse(null);
	}
}
