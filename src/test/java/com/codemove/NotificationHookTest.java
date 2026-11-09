package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.admin.LoginAdmin;
import com.codemove.member.LoginMember;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

/** 업무 흐름마다 알림이 쌓이는지: 견적 접수 → 견적 도착 → 예약 확정 → 일정 변경. 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = { "codemove.quote.upload-dir=build/test-uploads", "codemove.notification.reminder-cron=-",
		"codemove.notification.flush-cron=-" })
@AutoConfigureMockMvc
@Transactional
class NotificationHookTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpSession user;
	private MockHttpSession admin;
	private Long memberId;

	@BeforeEach
	void setUp() {
		String phone = String.format("010-1%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '알림테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "알림테스트", phone));
		Long adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '알림관리자', "
				+ "'010-9' || LPAD(MOD(?, 1000), 3, '0') || '-' || LPAD(MOD(?, 10000), 4, '0'))", adminId, "n" + adminId, adminId, adminId / 1000);
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "n" + adminId, "알림관리자", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);
	}

	/** 이 회원 관련 알림의 (받는 쪽 · 세부 종류) 목록 */
	private List<String> notifications() {
		return jdbc.queryForList("SELECT RECEIVER_TYPE || ':' || NTF_DETAIL FROM TBL_NOTIFICATION WHERE MEMBER_ID = ? ORDER BY NOTIFICATION_ID",
				String.class, memberId);
	}

	@Test
	void quoteToReservationFlowNotifies() throws Exception {
		LocalDate date = LocalDate.now().plusDays(12);
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(user))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(user)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"침대","size":"중형","count":1,"aiIndex":1,"review":false}],
				 "house":"원룸","pack":"일반포장","from":{"road":"서울 강남구 테헤란로 123","detail":""},
				 "to":{"road":"서울 서초구 서초대로 456","detail":""},"date":"%s","time":"오전"}
				""".formatted(date))).andExpect(status().isOk());
		Long quoteId = jdbc.queryForObject("SELECT Q.QUOTE_ID FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, memberId);
		String adminCode = "02"; // RECEIVER_TYPE 관리자 수신
		String memberCode = "01"; // RECEIVER_TYPE 사용자 수신
		assertThat(notifications()).containsExactly(adminCode + ":01");

		// 관리자 승인 → 고객에게 견적 도착
		mvc.perform(post("/admin/estimates/" + quoteId + "/approve").session(admin)).andExpect(status().isOk());
		assertThat(notifications()).endsWith(memberCode + ":02");

		// 고객 확정 → 관리자 · 고객 모두 예약 확정
		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, quoteId)
				.toLocalDateTime().toString();
		mvc.perform(post("/estimates/" + quoteId + "/confirm").session(user).param("updatedAt", updatedAt)).andExpect(status().isOk());
		assertThat(notifications()).contains(adminCode + ":04", memberCode + ":04");

		// 관리자 일정 변경 · 결제 완료 → 고객에게
		Long reservationId = jdbc.queryForObject("SELECT R.RESERVATION_ID FROM TBL_RESERVATION R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE Q.QUOTE_ID = ?",
				Long.class, quoteId);
		mvc.perform(post("/admin/reservations/" + reservationId + "/schedule").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\":\"" + date.format(DateTimeFormatter.ofPattern("yy.MM.dd")) + "\",\"time\":\"15:00\"}"))
				.andExpect(status().isOk());
		mvc.perform(post("/admin/reservations/" + reservationId + "/paid").session(admin)).andExpect(status().isOk());
		assertThat(notifications()).contains(memberCode + ":06", memberCode + ":10");
	}
}
