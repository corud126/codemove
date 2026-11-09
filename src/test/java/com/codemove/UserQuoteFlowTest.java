package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
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

import com.codemove.member.LoginMember;
import com.codemove.web.user.HomeController;

/** 사용자 견적: 검토 중 → 견적 도착 → 견적 확정(예약 생성). 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class UserQuoteFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpSession user;
	private Long memberId;

	@BeforeEach
	void login() {
		String phone = String.format("010-6%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '확정테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "확정테스트", phone));
	}

	private Long submit(LocalDate date) throws Exception {
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(user))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(user)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"침대","size":"대형","count":1,"aiIndex":1,"review":false}],
				 "house":"원룸","pack":"일반포장","from":{"road":"서울 강남구 테헤란로 123","detail":"301호"},
				 "to":{"road":"서울 서초구 서초대로 456","detail":""},"date":"%s","time":"오전"}
				""".formatted(date))).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT Q.QUOTE_ID FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, memberId);
	}

	/** 관리자 승인과 같은 상태로 (승인 관리자 없이) */
	private void approve(Long quoteId) {
		jdbc.update("UPDATE TBL_QUOTE SET STATUS = '02', APPROVED_AT = SYSDATE, UPDATED_AT = SYSDATE, EXPIRES_AT = SYSDATE + 3, "
				+ "WORK_TIME = '10:00', TO_LADDER_FLOOR = 8, TO_LADDER_COST = 20000 WHERE QUOTE_ID = ?", quoteId);
	}

	@Test
	void reviewThenArriveThenConfirm() throws Exception {
		LocalDate date = LocalDate.now().plusDays(14);
		Long id = submit(date);

		mvc.perform(get("/move").param("tab", "estimate").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("견적 요청을 검토하고 있어요")));
		// 검토 중에는 견적 상세를 열 수 없다
		mvc.perform(get("/estimates/" + id).session(user)).andExpect(redirectedUrl("/move?tab=estimate"));

		approve(id);
		String quoteNo = jdbc.queryForObject("SELECT QUOTE_NO FROM TBL_QUOTE WHERE QUOTE_ID = ?", String.class, id);
		mvc.perform(get("/move").param("tab", "estimate").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(quoteNo)))
				.andExpect(content().string(containsString("도착지 사다리 (8층)")))
				.andExpect(content().string(containsString("data-quote-confirm=\"/estimates/" + id + "/confirm\"")));
		mvc.perform(get("/estimates/" + id).session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("오전 10:00")));

		// 그 사이 견적이 바뀌었으면 막는다
		mvc.perform(post("/estimates/" + id + "/confirm").session(user).param("updatedAt", "2020-01-01T00:00"))
				.andExpect(status().isConflict());

		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, id)
				.toLocalDateTime().toString();
		mvc.perform(post("/estimates/" + id + "/confirm").session(user).param("updatedAt", updatedAt))
				.andExpect(status().isOk()).andExpect(jsonPath("$.redirect").value("/move?tab=estimate"));

		assertThat(jdbc.queryForObject("SELECT STATUS FROM TBL_QUOTE WHERE QUOTE_ID = ?", String.class, id)).isEqualTo("03");
		Map<String, Object> rsv = jdbc.queryForMap("SELECT R.* FROM TBL_RESERVATION R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE Q.QUOTE_ID = ?", id);
		assertThat((String) rsv.get("RESERVATION_NO")).matches("ME-" + date.toString().replace("-", "") + "-\\d{4}");
		assertThat(rsv.get("STATUS")).isEqualTo("01");
		assertThat(rsv.get("PAY_STATUS")).isEqualTo("01");
		assertThat(rsv.get("MOVE_TIME")).isEqualTo("10:00");

		// 두 번 확정할 수 없다
		mvc.perform(post("/estimates/" + id + "/confirm").session(user).param("updatedAt", updatedAt))
				.andExpect(status().isConflict());
		mvc.perform(get("/move").param("tab", "estimate").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("견적 확정")));
	}

	@Test
	void expiredAndOthersQuotes() throws Exception {
		Long id = submit(LocalDate.now().plusDays(14));
		approve(id);
		jdbc.update("UPDATE TBL_QUOTE SET EXPIRES_AT = SYSDATE - 1 WHERE QUOTE_ID = ?", id);
		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, id)
				.toLocalDateTime().toString();
		// 만료된 견적은 확정할 수 없다 (화면을 열면 만료로 바뀜)
		mvc.perform(get("/move").param("tab", "estimate").session(user)).andExpect(status().isOk())
				.andExpect(content().string(containsString("만료")));
		mvc.perform(post("/estimates/" + id + "/confirm").session(user).param("updatedAt", updatedAt))
				.andExpect(status().isConflict());

		// 다른 회원의 견적은 볼 수 없다
		MockHttpSession other = new MockHttpSession();
		other.setAttribute(HomeController.LOGIN_KEY, new LoginMember(-1L, "남", "010-0000-0000"));
		mvc.perform(get("/estimates/" + id).session(other)).andExpect(redirectedUrl("/move?tab=estimate"));
		mvc.perform(get("/move").param("tab", "estimate").session(other))
				.andExpect(status().isOk()).andExpect(content().string(containsString("아직 받은 견적이 없어요")));
		// 미리보기
		mvc.perform(get("/dev/user/move/estimate-detail")).andExpect(status().isOk());
	}
}
