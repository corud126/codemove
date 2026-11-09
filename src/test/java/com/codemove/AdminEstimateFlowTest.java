package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
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

/** 관리자 견적 관리: 초안 → 저장(합계 재계산) → 승인 → 승인 후 수정 → 삭제. 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class AdminEstimateFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpSession admin;
	private Long memberId;

	@BeforeEach
	void setUp() {
		String phone = String.format("010-7%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '관리테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		Long adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '테스트관리자', ?)",
				adminId, "t" + adminId, String.format("010-4%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000)));
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "t" + adminId, "테스트관리자", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);
	}

	/** 고객이 날짜 미정으로 견적 요청 → 견적서 초안 ID */
	private Long submitDraft() throws Exception {
		MockHttpSession user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "관리테스트", "010-0000-0000"));
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(user))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(user)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"냉장고","size":"중형","count":1,"aiIndex":0,"review":false}],
				 "house":"아파트","pack":"반포장","from":{"road":"서울 강남구 테헤란로 123","detail":""},
				 "to":{"road":"서울 서초구 서초대로 456","detail":""},"date":null,"time":"오전"}
				""")).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT Q.QUOTE_ID FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, memberId);
	}

	private static String sheet(String moveDate) {
		return """
				{"companyName":"명품익스프레스","customer":"관리테스트","phone":"010-0000-0000","estimateDate":"26.11.03",
				 "origin":"서울 강남구 테헤란로 123","moveDate":"%s","destination":"서울 서초구 서초대로 456","transportDate":"",
				 "items":[["냉장고","1대","침대","1개","","","",""],["","","","","박스","20개","",""]],
				 "options":[{"on1":false,"on2":false,"cost":"0"},{"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""},
				            {"on1":true,"on2":true,"cost":"20,000"},{"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""},
				            {"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""}],
				 "workMethod":"반포장","transports":[{"from":true,"to":false},{"from":false,"to":false},{"from":false,"to":true}],
				 "volume":"2.5톤","note":"파손 주의","movingCost":"500,000","fromLadderFloor":"3","departureLadder":"150,000",
				 "toLadderFloor":"","destinationLadder":"0"}
				""".formatted(moveDate);
	}

	@Test
	void reviewSaveApproveEditDelete() throws Exception {
		Long id = submitDraft();
		String url = "/admin/estimates/" + id;

		mvc.perform(get("/admin/estimates").session(admin))
				.andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("관리테스트")));
		mvc.perform(get(url).session(admin)).andExpect(status().isOk());

		// 날짜 미정 초안은 승인할 수 없다
		mvc.perform(post(url + "/approve").session(admin)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("작업일을 입력해야 승인할 수 있어요."));

		// 잘못된 작업 시각
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(sheet("26.12.20 09:00")))
				.andExpect(status().isBadRequest());

		// 저장: 총 비용 = 500,000 + 150,000 + 0 + 옵션 20,000 (서버 계산)
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(sheet("26.12.20 10:00")))
				.andExpect(status().isOk());
		Map<String, Object> q = jdbc.queryForMap("SELECT * FROM TBL_QUOTE WHERE QUOTE_ID = ?", id);
		assertThat(((BigDecimal) q.get("TOTAL_AMOUNT")).longValue()).isEqualTo(670_000);
		assertThat(((BigDecimal) q.get("DEPOSIT_AMOUNT")).longValue()).isEqualTo(67_000);
		assertThat(((BigDecimal) q.get("BALANCE_AMOUNT")).longValue()).isEqualTo(603_000);
		assertThat(q.get("WORK_TIME")).isEqualTo("10:00");
		assertThat(q.get("WORK_TYPE")).isEqualTo("02");
		assertThat(q.get("FROM_LADDER_USE")).isEqualTo("Y");
		assertThat(q.get("TO_STAIRS_USE")).isEqualTo("Y");
		assertThat((BigDecimal) q.get("VOLUME_TON")).isEqualByComparingTo("2.5");
		assertThat(jdbc.queryForList("SELECT CATEGORY || ':' || ITEM_NAME || ':' || QUANTITY || UNIT FROM TBL_QUOTE_ITEM WHERE QUOTE_ID = ? ORDER BY SORT_ORDER",
				String.class, id)).containsExactly("02:냉장고:1대", "01:침대:1개", "03:박스:20개");
		assertThat(jdbc.queryForList("SELECT OPTION_CODE FROM TBL_QUOTE_OPTION WHERE QUOTE_ID = ?", String.class, id)).containsExactly("04");
		assertThat(q.get("STATUS")).isEqualTo("01");

		// 승인: 상태 02, 만료 = 72시간 뒤 (작업일 전날이 더 늦음)
		mvc.perform(post(url + "/approve").session(admin)).andExpect(status().isOk());
		Map<String, Object> approved = jdbc.queryForMap("SELECT STATUS, APPROVED_AT, EXPIRES_AT, ADMIN_ID FROM TBL_QUOTE WHERE QUOTE_ID = ?", id);
		assertThat(approved.get("STATUS")).isEqualTo("02");
		assertThat(approved.get("ADMIN_ID")).isNotNull();
		LocalDateTime expires = ((Timestamp) approved.get("EXPIRES_AT")).toLocalDateTime();
		assertThat(expires).isBetween(LocalDateTime.now().plusHours(71), LocalDateTime.now().plusHours(73));
		mvc.perform(post(url + "/approve").session(admin)).andExpect(status().isConflict());

		// 승인 후 수정: 상태 유지, 만료 시각 다시 시작
		jdbc.update("UPDATE TBL_QUOTE SET EXPIRES_AT = SYSDATE + 1 WHERE QUOTE_ID = ?", id);
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(sheet("26.12.20 13:00")))
				.andExpect(status().isOk());
		Map<String, Object> edited = jdbc.queryForMap("SELECT STATUS, EXPIRES_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", id);
		assertThat(edited.get("STATUS")).isEqualTo("02");
		assertThat(((Timestamp) edited.get("EXPIRES_AT")).toLocalDateTime()).isAfter(LocalDateTime.now().plusHours(71));

		// 만료 시각이 지나면 목록을 볼 때 만료(04)로 바뀐다
		jdbc.update("UPDATE TBL_QUOTE SET EXPIRES_AT = SYSDATE - 1 WHERE QUOTE_ID = ?", id);
		mvc.perform(get("/admin/estimates").session(admin)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT STATUS FROM TBL_QUOTE WHERE QUOTE_ID = ?", String.class, id)).isEqualTo("04");

		// 고객이 요청한 견적은 삭제할 수 없다 (11/09 결정)
		mvc.perform(post(url + "/delete").session(admin)).andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE WHERE QUOTE_ID = ?", Integer.class, id)).isEqualTo(1);
	}

	/** 사진·PDF 등록 견적만 삭제할 수 있다 */
	@Test
	void onlyAdminRegisteredQuoteIsDeletable() throws Exception {
		Long id = jdbc.queryForObject("SELECT SEQ_QUOTE.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_QUOTE (QUOTE_ID, QUOTE_NO, SOURCE_TYPE, STATUS, CONTACT_NAME, ESTIMATE_DATE) "
				+ "VALUES (?, 'QT-TEST-' || ?, '02', '01', '수기고객', TRUNC(SYSDATE))", id, id);
		mvc.perform(get("/admin/estimates").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("data-href=\"/admin/estimates/" + id + "\"")));
		mvc.perform(post("/admin/estimates/" + id + "/delete").session(admin)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE WHERE QUOTE_ID = ?", Integer.class, id)).isZero();
	}

	/** 미리보기(/dev)는 모델 없이 예시 값(SAMPLE)으로 그린다 */
	@Test
	void previewRendersWithSamples() throws Exception {
		mvc.perform(get("/dev/admin/estimate/edit").param("state", "approved"))
				.andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("QT-20260325-0001")));
		mvc.perform(get("/dev/admin/estimate/list")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("data-live=\"false\"")));
		mvc.perform(get("/dev/admin/estimate/register")).andExpect(status().isOk());
		mvc.perform(get("/dev/user/quote/index").param("step", "6")).andExpect(status().isOk());
	}

	@Test
	void confirmedQuoteIsLocked() throws Exception {
		Long id = submitDraft();
		jdbc.update("UPDATE TBL_QUOTE SET STATUS = '03' WHERE QUOTE_ID = ?", id);
		mvc.perform(post("/admin/estimates/" + id).session(admin).contentType(MediaType.APPLICATION_JSON).content(sheet("26.12.20 10:00")))
				.andExpect(status().isConflict());
		mvc.perform(post("/admin/estimates/" + id + "/delete").session(admin)).andExpect(status().isConflict());
		mvc.perform(get("/admin/estimates/999999999").session(admin)).andExpect(status().isNotFound());
	}
}
