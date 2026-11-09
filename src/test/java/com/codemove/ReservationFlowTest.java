package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

/** 예약: 내 이사 예약 정보 → 예약 상세 → 요청사항 · 취소, 이사일 지나면 이사 완료. 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class ReservationFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpSession user;
	private Long memberId;

	@BeforeEach
	void login() {
		String phone = String.format("010-5%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '예약테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "예약테스트", phone));
	}

	/** 견적 요청 → (승인 상태로) → 견적 확정 → 예약 ID */
	private Long reserve(LocalDate date) throws Exception {
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(user))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(user)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"소파","size":"중형","count":1,"aiIndex":2,"review":false}],
				 "house":"빌라","pack":"반포장","from":{"road":"서울 마포구 월드컵로 10","detail":""},
				 "to":{"road":"서울 서대문구 연희로 20","detail":"2층"},"date":"%s","time":"오후"}
				""".formatted(date))).andExpect(status().isOk());
		Long quoteId = jdbc.queryForObject("SELECT MAX(Q.QUOTE_ID) FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, memberId);
		jdbc.update("UPDATE TBL_QUOTE SET STATUS = '02', APPROVED_AT = SYSDATE, UPDATED_AT = SYSDATE, EXPIRES_AT = SYSDATE + 3, WORK_TIME = '13:00' WHERE QUOTE_ID = ?",
				quoteId);
		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, quoteId)
				.toLocalDateTime().toString();
		mvc.perform(post("/estimates/" + quoteId + "/confirm").session(user).param("updatedAt", updatedAt)).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT R.RESERVATION_ID FROM TBL_RESERVATION R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE Q.QUOTE_ID = ?",
				Long.class, quoteId);
	}

	@Test
	void bookingDetailMemoCancel() throws Exception {
		Long id = reserve(LocalDate.now().plusDays(10));
		String no = jdbc.queryForObject("SELECT RESERVATION_NO FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id);

		mvc.perform(get("/move").param("tab", "booking").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/reservations/" + id)))
				.andExpect(content().string(containsString("D-10")))
				.andExpect(content().string(containsString("마포구")))
				.andExpect(content().string(containsString("오후 1:00")));
		mvc.perform(get("/reservations/" + id).session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(no)))
				.andExpect(content().string(containsString("결제 대기")));

		mvc.perform(post("/reservations/" + id + "/memo").session(user).param("memo", "  주차는 지하 2층  "))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT MOVE_MEMO FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id))
				.isEqualTo("주차는 지하 2층");
		mvc.perform(post("/reservations/" + id + "/memo").session(user).param("memo", "가".repeat(501)))
				.andExpect(status().isBadRequest());

		// 다른 회원은 볼 수 없다
		MockHttpSession other = new MockHttpSession();
		other.setAttribute(HomeController.LOGIN_KEY, new LoginMember(-1L, "남", "010-0000-0000"));
		mvc.perform(get("/reservations/" + id).session(other)).andExpect(redirectedUrl("/move?tab=booking"));
		mvc.perform(post("/reservations/" + id + "/cancel").session(other)).andExpect(status().isBadRequest());

		mvc.perform(post("/reservations/" + id + "/cancel").session(user))
				.andExpect(status().isOk()).andExpect(jsonPath("$.redirect").value("/move?tab=booking"));
		Map<String, Object> row = jdbc.queryForMap("SELECT STATUS, PAY_STATUS, CANCELLED_BY, CANCELLED_AT FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", id);
		assertThat(row.get("STATUS")).isEqualTo("03");
		assertThat(row.get("PAY_STATUS")).isEqualTo("03");
		assertThat(row.get("CANCELLED_BY")).isEqualTo("01");
		assertThat(row.get("CANCELLED_AT")).isNotNull();
		mvc.perform(post("/reservations/" + id + "/cancel").session(user)).andExpect(status().isConflict());
		mvc.perform(get("/move").param("tab", "booking").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("아직 확정된 예약이 없어요")));
		// 취소된 예약 상세에는 취소·수정 버튼이 없다
		mvc.perform(get("/reservations/" + id).session(user))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString("data-reservation-cancel"))));
	}

	@Test
	void pastMoveBecomesCompleted() throws Exception {
		mvc.perform(get("/move").param("tab", "done").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("아직 완료된 이사가 없어요")));
		Long id = reserve(LocalDate.now().plusDays(5));
		jdbc.update("UPDATE TBL_RESERVATION SET MOVE_DATE = TRUNC(SYSDATE) - 1 WHERE RESERVATION_ID = ?", id);

		mvc.perform(get("/move").param("tab", "done").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("서대문구")))
				.andExpect(content().string(containsString("/more/reviews/new?reservation=" + id)));
		assertThat(jdbc.queryForObject("SELECT STATUS FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id)).isEqualTo("02");
		// 미리보기
		mvc.perform(get("/dev/user/move/reservation-detail")).andExpect(status().isOk());
		mvc.perform(get("/dev/user/move/index").param("tab", "done")).andExpect(status().isOk());
	}
}
