package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.admin.LoginAdmin;
import com.codemove.member.LoginMember;
import com.codemove.notification.HistoryQuery;
import com.codemove.notification.NotificationDetail;
import com.codemove.notification.NotificationService;
import com.codemove.notification.NotificationSettingService;
import com.codemove.notification.NotificationViews;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

/** 알림: 회원 · 관리자 알림 만들기, 사용자 알림 화면 · 헤더 종, 관리자 패널 · 발송 내역 · 설정, D-3. 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = { "codemove.notification.reminder-cron=-", "codemove.notification.flush-cron=-" })
@AutoConfigureMockMvc
@Transactional
class NotificationFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationSettingService settingService;

	private MockHttpSession user;
	private Long memberId;
	private String memberName;
	private MockHttpSession admin;
	private Long adminId;

	@BeforeEach
	void setUp() {
		memberName = "알림" + ThreadLocalRandom.current().nextInt(100000, 1000000);
		memberId = newMember(memberName);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, memberName, "010-0000-0000"));

		adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '알림관리자', ?)",
				adminId, "n" + adminId, randomPhone("010-6"));
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "n" + adminId, "알림관리자", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);

		// 야간 보류에 걸리지 않게 (테스트 시각과 무관하게)
		settingService.save(true, false, "22:00", "08:00", true, adminId);
	}

	private Long newMember(String name) {
		Long id = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, ?, ?, 'Y', SYSDATE)",
				id, name, randomPhone("010-7"));
		return id;
	}

	private static String randomPhone(String prefix) {
		return String.format("%s%03d-%04d", prefix, ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
	}

	private Map<String, Object> row(Long id) {
		return jdbc.queryForMap("SELECT * FROM TBL_NOTIFICATION WHERE NOTIFICATION_ID = ?", id);
	}

	@Test
	void notifyMemberAndUserScreen() throws Exception {
		Long quote = notificationService.notifyMember(memberId, NotificationDetail.QUOTE_ARRIVED.code(), "견적서가 도착했어요",
				"요청하신 견적이 도착했어요. 확인해 주세요.", "/estimates/9");
		Long booked = notificationService.notifyMember(memberId, "04", "이사 예약이 확정되었어요", "예약 내용을 확인해 주세요.", "/reservations/7");
		Map<String, Object> r = row(quote);
		assertThat(r.get("RECEIVER_TYPE")).isEqualTo("01");
		assertThat(r.get("NTF_TYPE")).isEqualTo("01");
		assertThat(r.get("NTF_DETAIL")).isEqualTo("02");
		assertThat(r.get("NTF_CHANNEL")).isEqualTo("02");
		assertThat(r.get("IS_READ")).isEqualTo("N");
		assertThat(r.get("SENT_AT")).isNotNull();
		assertThat(r.get("LINK_URL")).isEqualTo("/estimates/9");

		// 회원용이 아닌 종류 · 빈 제목은 개발 오류
		assertThatThrownBy(() -> notificationService.notifyMember(memberId, "13", "x", "y", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> notificationService.notifyMember(memberId, "02", " ", "y", null))
				.isInstanceOf(IllegalArgumentException.class);

		// 목록 · 헤더 종 (unreadNotifications)
		mvc.perform(get("/notifications").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("견적서가 도착했어요")))
				.andExpect(content().string(containsString("이사 예약이 확정되었어요")))
				.andExpect(content().string(containsString("data-id=\"" + quote + "\"")))
				.andExpect(content().string(containsString("/estimates/9")))
				.andExpect(content().string(containsString("data-tab=\"move\"")))
				.andExpect(content().string(containsString("방금 전")));
		mvc.perform(get("/more").session(user))
				.andExpect(model().attribute("unreadNotifications", 2))
				.andExpect(content().string(containsString("notification-dot")));

		// 읽음 · 다른 회원 · 비로그인
		mvc.perform(post("/notifications/" + quote + "/read").session(user))
				.andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1));
		assertThat(row(quote).get("IS_READ")).isEqualTo("Y");
		MockHttpSession other = new MockHttpSession();
		other.setAttribute(HomeController.LOGIN_KEY, new LoginMember(-1L, "남", "010-0000-0000"));
		mvc.perform(post("/notifications/" + booked + "/read").session(other)).andExpect(status().isBadRequest());
		mvc.perform(post("/notifications/" + booked + "/read")).andExpect(status().isUnauthorized());

		// 모두 읽음 → 종 점 없음
		mvc.perform(post("/notifications/read-all").session(user)).andExpect(status().isOk());
		assertThat(row(booked).get("IS_READ")).isEqualTo("Y");
		mvc.perform(get("/more").session(user))
				.andExpect(model().attribute("unreadNotifications", 0))
				.andExpect(content().string(not(containsString("notification-dot"))));
	}

	@Test
	void customerSendingFollowsSettings() {
		// 야간 보류 중 → SENT_AT NULL, 보류가 끝나면 일괄 발송
		LocalTime now = LocalTime.now();
		String start = now.minusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"));
		String end = now.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"));
		settingService.save(true, true, start, end, true, adminId);
		Long held = notificationService.notifyMember(memberId, "15", "상담 답변 도착", "상담원이 답변을 보냈어요.", "/chat");
		assertThat(row(held).get("SENT_AT")).isNull();
		assertThat(notificationService.flushHeld()).isZero();
		settingService.save(true, false, start, end, true, adminId);
		assertThat(notificationService.flushHeld()).isGreaterThanOrEqualTo(1);
		assertThat(row(held).get("SENT_AT")).isNotNull();
		assertThat(row(held).get("NTF_CHANNEL")).isEqualTo("02");

		// 자동 발송 꺼짐 → 앱 알림만
		settingService.save(false, false, start, end, true, adminId);
		Long appOnly = notificationService.notifyMember(memberId, "06", "이사 일정이 변경되었어요", "바뀐 일정을 확인해 주세요.", null);
		assertThat(row(appOnly).get("NTF_CHANNEL")).isEqualTo("01");
		assertThat(row(appOnly).get("SENT_AT")).isNotNull();
	}

	@Test
	void adminPanelReadAndPreview() throws Exception {
		Long chat = notificationService.notifyAdmins(memberId, "13", "채팅 상담 대기", memberName + " 고객님이 상담원 연결을 요청했습니다.",
				"/admin/chats/" + memberId);
		Long joined = notificationService.notifyAdmins(null, NotificationDetail.MEMBER_JOINED.code(), "신규 가입", memberName + " 고객님이 회원으로 가입했습니다.", null);
		Map<String, Object> r = row(chat);
		assertThat(r.get("RECEIVER_TYPE")).isEqualTo("02");
		assertThat(r.get("NTF_TYPE")).isEqualTo("03");
		assertThat(r.get("IS_IMPORTANT")).isEqualTo("Y");
		assertThat(r.get("NTF_CHANNEL")).isEqualTo("01");
		assertThat(((Number) r.get("MEMBER_ID")).longValue()).isEqualTo(memberId);
		assertThat(row(joined).get("MEMBER_ID")).isNull();

		NotificationViews.AdminPanel panel = notificationService.adminPanel();
		assertThat(panel.groups()).extracting(NotificationViews.PanelGroup::label).containsExactly("오늘", "어제", "이전");
		NotificationViews.PanelItem item = panel.groups().get(0).items().stream().filter(i -> chat.equals(i.id())).findFirst().orElseThrow();
		assertThat(item.detail()).isEqualTo("채팅 대기");
		assertThat(item.icon()).isEqualTo("chat");
		assertThat(item.important()).isTrue();
		assertThat(item.unread()).isTrue();
		assertThat(panel.unread()).isGreaterThanOrEqualTo(2);

		// 관리자 화면마다 패널 · 종 점
		mvc.perform(get("/admin/notifications").session(admin))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("adminNotificationPanel"))
				.andExpect(content().string(containsString(memberName + " 고객님이 상담원 연결을 요청했습니다.")))
				.andExpect(content().string(containsString("data-id=\"" + chat + "\"")))
				.andExpect(content().string(containsString("/admin/chats/" + memberId)));

		mvc.perform(post("/admin/notifications/" + chat + "/read").session(admin)).andExpect(status().isOk());
		assertThat(row(chat).get("IS_READ")).isEqualTo("Y");
		mvc.perform(post("/admin/notifications/-1/read").session(admin)).andExpect(status().isBadRequest());
		mvc.perform(post("/admin/notifications/read-all").session(admin)).andExpect(status().isOk());
		assertThat(row(joined).get("IS_READ")).isEqualTo("Y");
		assertThat(notificationService.adminPanel().unread()).isZero();
		mvc.perform(get("/admin/notifications").session(admin))
				.andExpect(content().string(containsString("border-white bg-[#f5831a]\" hidden=\"hidden\"")));

		// 미리보기: 모델이 없으면 예시 값
		mvc.perform(get("/dev/admin/notification/home")).andExpect(status().isOk())
				.andExpect(content().string(containsString("강민주 고객님이 상담원 연결을 요청했습니다.")))
				.andExpect(content().string(containsString("상담 답변 도착 · 알림톡")));
		mvc.perform(get("/dev/user/more/notifications")).andExpect(status().isOk())
				.andExpect(content().string(containsString("계약금 결제가 완료되었어요")));
		mvc.perform(get("/dev/user/more/index")).andExpect(status().isOk());
	}

	@Test
	void historySearchFilterPaging() throws Exception {
		for (int i = 0; i < 25; i++) {
			notificationService.notifyMember(memberId, "01", "견적 요청 접수 " + i, "견적 요청이 정상적으로 접수되었습니다.", "/move?tab=estimate");
		}
		// 다른 회원 알림은 이름 검색에 나오지 않음
		notificationService.notifyMember(newMember("다른회원"), "01", "견적 요청 접수", "다른 회원", null);
		// 관리자 수신 알림은 발송 내역에 나오지 않음
		notificationService.notifyAdmins(memberId, "01", "견적 접수", memberName + " 고객님의 견적 요청이 접수되었습니다.", null);

		NotificationViews.History first = notificationService.history(memberName, null, null, null);
		assertThat(first.total()).isEqualTo(25);
		assertThat(first.rows()).hasSize(20);
		assertThat(first.nextCursor()).isNotNull();
		NotificationViews.HistoryRow top = first.rows().get(0);
		assertThat(top.name()).isEqualTo(memberName);
		assertThat(top.title()).isEqualTo("견적 요청 접수 24");
		assertThat(top.detail()).isEqualTo("견적 접수");
		assertThat(top.channel()).isEqualTo("알림톡");
		assertThat(top.result()).isEqualTo("sent");
		assertThat(top.group()).isEqualTo("today");
		assertThat(top.phone4()).hasSize(4);
		NotificationViews.History second = notificationService.history(memberName, null, null, first.nextCursor());
		assertThat(second.rows()).hasSize(5);
		assertThat(second.nextCursor()).isNull();
		assertThat(second.rows().get(4).title()).isEqualTo("견적 요청 접수 0");

		assertThat(notificationService.history(memberName, "알림톡", null, null).total()).isEqualTo(25);
		assertThat(notificationService.history(memberName, "02", "sent", null).total()).isEqualTo(25);
		assertThat(notificationService.history(memberName, "문자", null, null).total()).isZero();
		assertThat(notificationService.history(memberName, null, "held", null).total()).isZero();
		assertThat(notificationService.history(memberName, null, "sms", null).total()).isZero();

		// 날짜 검색 (오늘) · 없는 날짜
		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yy.MM.dd"));
		assertThat(notificationService.history(today, null, null, null).total()).isGreaterThanOrEqualTo(26);
		assertThat(notificationService.history("99.01.01", null, null, null).total()).isZero();

		// JSON
		mvc.perform(get("/admin/notifications/page").param("q", memberName).session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(25))
				.andExpect(jsonPath("$.rows.length()").value(20))
				.andExpect(jsonPath("$.rows[0].name").value(memberName))
				.andExpect(jsonPath("$.nextCursor").isNumber());
		mvc.perform(get("/admin/notifications").session(admin))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("history", "notiSettings"))
				.andExpect(content().string(containsString("data-live=\"Y\"")));
	}

	@Test
	void datePatterns() {
		assertThat(HistoryQuery.of("26.11.02", null, null, 30).datePatterns()).contains("261102");
		assertThat(HistoryQuery.of("11.2", null, null, 30).datePatterns()).contains("__1102", "__112_", "1102__", "112___");
		assertThat(HistoryQuery.of("3월", null, null, 30).datePatterns()).containsExactly("__03__");
		assertThat(HistoryQuery.of("260325", null, null, 30).datePatterns()).contains("260325%");
		HistoryQuery name = HistoryQuery.of("홍길동", "02", "bogus", 30);
		assertThat(name.name()).isEqualTo("홍길동");
		assertThat(name.datePatterns()).isNull();
		assertThat(name.result()).isNull();
		assertThat(name.recentDays()).isEqualTo(30);
		assertThat(HistoryQuery.of("26.3", null, null, 30).recentDays()).isZero();
	}

	@Test
	void settingsAndPushToggle() throws Exception {
		mvc.perform(post("/admin/notifications/settings").session(admin)
				.param("enabled", "true").param("quiet", "true").param("quietStart", "21:30").param("quietEnd", "07:00").param("sms", "false"))
				.andExpect(status().isOk());
		Map<String, Object> s = jdbc.queryForMap("SELECT * FROM (SELECT * FROM TBL_NOTIFICATION_SETTING ORDER BY SETTING_ID) WHERE ROWNUM = 1");
		assertThat(s.get("IS_ACTIVE")).isEqualTo("Y");
		assertThat(s.get("QUIET_ENABLED")).isEqualTo("Y");
		assertThat(s.get("QUIET_START")).isEqualTo("21:30");
		assertThat(s.get("QUIET_END")).isEqualTo("07:00");
		assertThat(s.get("SMS_FALLBACK")).isEqualTo("N");
		assertThat(((Number) s.get("ADMIN_ID")).longValue()).isEqualTo(adminId);
		mvc.perform(post("/admin/notifications/settings").session(admin)
				.param("enabled", "true").param("quiet", "true").param("quietStart", "25:00").param("quietEnd", "07:00").param("sms", "true"))
				.andExpect(status().isBadRequest());

		mvc.perform(post("/admin/notifications/push").session(admin).param("enabled", "false")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT PUSH_ENABLED FROM TBL_ADMIN WHERE ADMIN_ID = ?", String.class, adminId)).isEqualTo("N");
		// 알림 받기를 꺼도 패널에는 쌓인다
		Long id = notificationService.notifyAdmins(null, "14", "신규 가입", "새 회원이 가입했습니다.", "/admin/members");
		assertThat(notificationService.adminPanel().groups().get(0).items()).extracting(NotificationViews.PanelItem::id).contains(id);

		mvc.perform(get("/admin/notifications").param("tab", "settings").session(admin))
				.andExpect(status().isOk())
				.andExpect(model().attribute("notiSettings", new NotificationViews.Settings(false, true, true, "21:30", "07:00", false)))
				.andExpect(content().string(containsString("오후 09:30")))
				.andExpect(content().string(containsString("aria-pressed=\"false\"")));
		mvc.perform(post("/admin/notifications/push").session(admin).param("enabled", "true")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT PUSH_ENABLED FROM TBL_ADMIN WHERE ADMIN_ID = ?", String.class, adminId)).isEqualTo("Y");

		assertThat(settingService.isQuietNow(settingService.get(), LocalTime.of(23, 0))).isTrue();
		assertThat(settingService.isQuietNow(settingService.get(), LocalTime.of(6, 59))).isTrue();
		assertThat(settingService.isQuietNow(settingService.get(), LocalTime.of(7, 0))).isFalse();
		assertThat(settingService.isQuietNow(settingService.get(), LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void moveReminderD3() {
		Long requestId = jdbc.queryForObject("SELECT SEQ_MOVE_REQUEST.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MOVE_REQUEST (REQUEST_ID, MEMBER_ID, HOUSE_TYPE, PACK_TYPE, FROM_ADDRESS, TO_ADDRESS) "
				+ "VALUES (?, ?, '01', '01', '서울 마포구 월드컵로 10', '서울 서대문구 연희로 20')", requestId, memberId);
		Long reservationId = jdbc.queryForObject("SELECT SEQ_RESERVATION.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_RESERVATION (RESERVATION_ID, RESERVATION_NO, REQUEST_ID, STATUS, PAY_STATUS, MOVE_DATE, MOVE_TIME) "
				+ "VALUES (?, ?, ?, '01', '01', TRUNC(SYSDATE) + 3, '13:00')", reservationId, "NT-" + reservationId, requestId);

		assertThat(notificationService.sendMoveReminders()).isGreaterThanOrEqualTo(1);
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT * FROM TBL_NOTIFICATION WHERE MEMBER_ID = ? AND NTF_DETAIL = '07'", memberId);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get("LINK_URL")).isEqualTo("/reservations/" + reservationId);
		assertThat(rows.get(0).get("NTF_TITLE")).isEqualTo("이사까지 3일 남았어요");
		assertThat((String) rows.get(0).get("NTF_CONTENT")).contains("오후 1:00");
		// 같은 예약에는 한 번만
		notificationService.sendMoveReminders();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_NOTIFICATION WHERE MEMBER_ID = ? AND NTF_DETAIL = '07'",
				Integer.class, memberId)).isEqualTo(1);
	}
}
