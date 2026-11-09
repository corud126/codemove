package com.codemove.notification;

import java.util.List;

/**
 * 알림 화면 표시값. 값은 화면 글자 그대로. SAMPLE: 미리보기(/dev)용 예시 (모델이 없을 때 템플릿이 사용)
 */
public final class NotificationViews {

	private NotificationViews() {
	}

	/**
	 * 사용자 알림 한 줄 (user/more/notifications.html).
	 * icon: schedule · estimate · payment · default / tab: move (내 이사) / time: "14시간 전"
	 */
	public record UserItem(Long id, String title, String content, String time, String icon, String tab, String link,
			boolean unread) {

		public static final List<UserItem> SAMPLE = List.of(
				new UserItem(1L, "이사 예약이 확정되었어요", "3월 18일 오전 10시 예약과 방문 주소를 확인해주세요.", "14시간 전", "schedule", "move",
						"/reservations/1", true),
				new UserItem(2L, "견적서가 도착했어요", "담당자가 확인한 견적 금액과 작업 내용을 확인해주세요.", "2일 전", "estimate", "move",
						"/estimates/1", false),
				new UserItem(3L, "계약금 결제가 완료되었어요", "결제 내역과 이사 당일 잔금을 확인할 수 있어요.", "5일 전", "payment", "move",
						"/reservations/1", false));
	}

	/** 관리자 알림 패널 (A-019): 오늘 · 어제 · 이전 묶음 + 안 읽음 수 */
	public record AdminPanel(List<PanelGroup> groups, int unread) {

		public static final AdminPanel SAMPLE = new AdminPanel(List.of(
				new PanelGroup("오늘", List.of(
						new PanelItem(null, "채팅 대기", "강민주 고객님이 상담원 연결을 요청했습니다.", "10분 전", "chat", "/admin/chats/1011", true, true),
						new PanelItem(null, "오늘의 일정", "오늘 이서준 고객님의 이사가 있습니다.", "2시간 전", "truck", "/admin/schedule", false, true),
						new PanelItem(null, "계약금 결제", "최지우 고객님이 계약금 결제를 완료했습니다.", "3시간 전", "card", "/admin/reservations", false, true),
						new PanelItem(null, "예약 확정", "최지우 고객님이 견적을 확정해 예약이 생성되었습니다.", "5시간 전", "calendarCheck", "/admin/reservations", false, true))),
				new PanelGroup("어제", List.of(
						new PanelItem(null, "견적 접수", "홍길동 고객님의 견적 요청이 접수되었습니다.", "오후 4:20", "document", "/admin/estimates", false, false),
						new PanelItem(null, "이사 D-3", "김명품 고객님의 이사가 3일 남았습니다.", "오전 9:00", "clock", "/admin/reservations", false, false))),
				new PanelGroup("이전", List.of(
						new PanelItem(null, "예약 취소", "박철수 고객님이 11월 14일 이사 예약을 취소했습니다.", "10.26", "x", "/admin/reservations", false, false),
						new PanelItem(null, "신규 가입", "윤서연 고객님(010-2345-4412)이 회원으로 가입했습니다.", "10.22", "user", "/admin/members", false, false),
						new PanelItem(null, "견적 접수", "정하늘 고객님의 견적 요청이 접수되었습니다.", "10.19", "document", "/admin/estimates", false, false)))),
				4);
	}

	public record PanelGroup(String label, List<PanelItem> items) {
	}

	/** detail: NTF_DETAIL 관리자 이름 · time: 오늘 "10분 전", 어제 "오후 4:20", 이전 "10.26" · link 없으면 null */
	public record PanelItem(Long id, String detail, String content, String time, String icon, String link, boolean important,
			boolean unread) {
	}

	/**
	 * 관리자 발송 내역 (SCR-A-013) 한 페이지.
	 * todaySent · held · sms: 현황 3칸 / total: 조건에 맞는 전체 수 / nextCursor: 다음 페이지 기준 (없으면 null)
	 */
	public record History(int todaySent, int held, int sms, int total, List<HistoryRow> rows, Long nextCursor) {

		public boolean hasMore() {
			return nextCursor != null;
		}

		public static final History SAMPLE = new History(45, 2, 3, 14, List.of(
				row(1, "강민주", "1234", "상담 답변 도착", "상담 답변 도착", "상담원이 답변을 보냈습니다. 채팅에서 확인해 주세요.", "알림톡", "-", "held", "26.11.02", "22:41", "today"),
				row(2, "김명품", "5821", "견적 변경", "견적이 변경되었어요", "승인된 견적 내용이 수정되었습니다. 다시 확인해 주세요.", "알림톡", "-", "held", "26.11.02", "22:15", "today"),
				row(3, "이서준", "3307", "이사 당일", "오늘은 이사 당일입니다", "담당 기사 정보를 확인해 주세요.", "알림톡", "08:00", "sent", "26.11.02", "08:00", "today"),
				row(4, "최지우", "9048", "견적 접수", "견적 요청 접수", "견적 요청이 정상적으로 접수되었습니다.", "알림톡", "08:00", "sent", "26.11.02", "01:12", "today"),
				row(5, "김명품", "5821", "예약 확정", "이사 예약이 확정되었어요", "견적 확정으로 이사 예약이 완료되었습니다.", "알림톡", "16:05", "sent", "26.11.01", "16:05", "yesterday"),
				row(6, "박철수", "7712", "계약금 결제", "계약금 결제가 완료되었어요", "계약금 결제가 확인되었습니다.", "문자", "15:02", "sms", "26.11.01", "15:02", "yesterday"),
				row(7, "정현우", "2286", "견적 도착", "견적서가 도착했어요", "요청하신 견적이 도착했습니다. 확인해 주세요.", "알림톡", "11:20", "sent", "26.10.31", "11:20", "older"),
				row(8, "홍길동", "6650", "견적 도착", "견적서가 도착했어요", "요청하신 견적이 도착했습니다. 확인해 주세요.", "알림톡", "10:47", "sent", "26.10.31", "10:47", "older"),
				row(9, "최지우", "9048", "이사 D-3", "이사 3일 전", "이사 예정일이 3일 남았습니다. 준비 사항을 확인해 주세요.", "알림톡", "09:00", "sent", "26.10.30", "09:00", "older"),
				row(10, "송지호", "4419", "일정 변경", "이사 일정 변경", "이사 일정이 11월 12일 오전 9시로 변경되었습니다.", "문자", "14:12", "sms", "26.10.29", "14:12", "older"),
				row(11, "윤아름", "8803", "이사 완료", "이사 완료", "이사 완료 처리가 되었습니다. 서비스 이용은 어떠셨나요?", "알림톡", "18:30", "sent", "26.10.28", "18:30", "older"),
				row(12, "한유진", "1575", "예약 취소", "예약이 취소되었어요", "11월 8일 이사 예약이 취소되었습니다.", "알림톡", "13:44", "sent", "26.10.27", "13:44", "older"),
				row(13, "윤아름", "8803", "예약 확정", "이사 예약이 확정되었어요", "견적 확정으로 이사 예약이 완료되었습니다.", "알림톡", "10:00", "sent", "26.10.25", "10:00", "older"),
				row(14, "송지호", "4419", "견적 접수", "견적 요청 접수", "견적 요청이 정상적으로 접수되었습니다.", "알림톡", "10:12", "sent", "26.10.24", "10:12", "older")),
				null);

		private static HistoryRow row(long id, String name, String phone4, String detail, String title, String content,
				String channel, String sentTime, String result, String date, String time, String group) {
			return new HistoryRow(id, name, phone4, detail, title, content, channel, result, date, time, group,
					"-".equals(sentTime) ? "" : date + " " + sentTime);
		}
	}

	/**
	 * 발송 내역 한 줄. detail: NTF_DETAIL 고객 쪽 이름 · channel: 앱 · 알림톡 · 문자 · result: sent · held · sms
	 * date: 작성일 "26.11.02" · time: 작성 "22:41" · group: today · yesterday · older · sent: 발송 "26.11.02 08:00" (보류 중이면 "")
	 */
	public record HistoryRow(Long id, String name, String phone4, String detail, String title, String content, String channel,
			String result, String date, String time, String group, String sent) {
	}

	/** 알림 설정 탭. quietStart · quietEnd: "22:00" */
	public record Settings(boolean push, boolean enabled, boolean quiet, String quietStart, String quietEnd, boolean sms) {

		public static final Settings SAMPLE = new Settings(true, true, true, "22:00", "08:00", true);

		/** "22:00" → "오후 10:00" (화면 표시) */
		public static String display(String hhmm) {
			if (hhmm == null || !hhmm.matches("\\d{2}:\\d{2}")) {
				return "";
			}
			int h = Integer.parseInt(hhmm.substring(0, 2));
			return (h >= 12 ? "오후 " : "오전 ") + String.format("%02d", h % 12 == 0 ? 12 : h % 12) + hhmm.substring(2);
		}
	}
}
