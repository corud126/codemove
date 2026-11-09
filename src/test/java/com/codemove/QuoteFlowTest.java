package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.codemove.request.PriceCalculator;
import com.codemove.web.user.HomeController;

/** 이사 견적 요청: 영상 → 분석 → 예상 금액 → 제출(요청 + 견적서 초안). 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class QuoteFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PriceCalculator priceCalculator;

	private MockHttpSession session;
	private Long memberId;
	private String phone;

	/** 로그인한 회원 (테스트 안에서 만들고 롤백) */
	@BeforeEach
	void login() {
		phone = String.format("010-8%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '견적테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		session = new MockHttpSession();
		session.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "견적테스트", phone));
	}

	private static String form(String date) {
		String dateJson = date == null ? "null" : "\"" + date + "\"";
		return """
				{"items":[
				  {"name":"냉장고","size":"중형","count":1,"aiIndex":0,"review":false},
				  {"name":"소파","size":"중형","count":1,"aiIndex":2,"review":true},
				  {"name":"이사박스","size":"중형","count":13,"aiIndex":3,"review":false},
				  {"name":"피아노","size":"대형","count":1,"aiIndex":null,"review":false}],
				 "house":"원룸","pack":"일반포장",
				 "from":{"road":"서울 강남구 테헤란로 123","detail":"301호"},
				 "to":{"road":"서울 서초구 서초대로 456","detail":""},
				 "date":%s,"time":"오전"}
				""".formatted(dateJson);
	}

	private void uploadAndAnalyze() throws Exception {
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "우리집.mp4", "video/mp4", new byte[] { 1, 2, 3 }))
				.session(session))
				.andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("우리집.mp4"));
		mvc.perform(post("/quote/analyze").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].name").value("냉장고"))
				.andExpect(jsonPath("$.items[0].size").value("중형"))
				.andExpect(jsonPath("$.items[2].review").value(true));
	}

	@Test
	void tonRule() {
		assertThat(priceCalculator.ton(new BigDecimal("4.0"))).isEqualByComparingTo("1.0");
		assertThat(priceCalculator.ton(new BigDecimal("7.7"))).isEqualByComparingTo("1.5");
		assertThat(priceCalculator.ton(new BigDecimal("12.8"))).isEqualByComparingTo("2.5");
		assertThat(priceCalculator.ton(new BigDecimal("30.0"))).isEqualByComparingTo("5.0");
	}

	@Test
	void submitCreatesRequestAndDraftQuote() throws Exception {
		uploadAndAnalyze();
		String date = LocalDate.now().plusDays(10).toString();

		mvc.perform(post("/quote/estimate").session(session).contentType(MediaType.APPLICATION_JSON).content(form(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(16));

		mvc.perform(post("/quote").session(session).contentType(MediaType.APPLICATION_JSON).content(form(date)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.redirect").value("/quote/complete"));
		assertThat(session.getAttribute("quoteSession")).isNull();

		Map<String, Object> req = jdbc.queryForMap("SELECT * FROM TBL_MOVE_REQUEST WHERE MEMBER_ID = ?", memberId);
		assertThat(req.get("HOUSE_TYPE")).isEqualTo("01");
		assertThat(req.get("PACK_TYPE")).isEqualTo("01");
		assertThat(req.get("PREFER_TIME")).isEqualTo("01");
		assertThat(req.get("FROM_DETAIL")).isEqualTo("301호");
		assertThat(req.get("TO_DETAIL")).isNull();
		// 1.8 + 2.1 + 4.6 + 0.5×1.25 = 9.13 CBM
		assertThat((BigDecimal) req.get("AI_CBM")).isEqualByComparingTo("9.13");
		// AI 품목 3개 신뢰도 평균 (사용자 추가 품목 제외)
		assertThat(((BigDecimal) req.get("AI_CONFIDENCE")).intValue()).isEqualTo((96 + 62 + 91) / 3);
		Long requestId = ((BigDecimal) req.get("REQUEST_ID")).longValue();

		assertThat(jdbc.queryForList("SELECT ITEM_NAME || ':' || SOURCE || ':' || NEEDS_REVIEW FROM TBL_REQUEST_ITEM WHERE REQUEST_ID = ? ORDER BY RE_ITEM_ID",
				String.class, requestId)).containsExactly("냉장고:01:N", "소파:01:Y", "이사박스:01:N", "피아노:02:N");
		assertThat(jdbc.queryForObject("SELECT FILE_NAME FROM TBL_RE_VIDEO WHERE REQUEST_ID = ?", String.class, requestId))
				.isEqualTo("우리집.mp4");

		Map<String, Object> quote = jdbc.queryForMap("SELECT * FROM TBL_QUOTE WHERE REQUEST_ID = ?", requestId);
		assertThat(quote.get("STATUS")).isEqualTo("01");
		assertThat(quote.get("SOURCE_TYPE")).isEqualTo("01");
		assertThat((String) quote.get("QUOTE_NO")).matches("QT-\\d{8}-\\d{4}");
		assertThat(quote.get("CONTACT_NAME")).isEqualTo("견적테스트");
		assertThat(quote.get("CONTACT_PHONE")).isEqualTo(phone);
		assertThat(quote.get("FROM_ADDRESS")).isEqualTo("서울 강남구 테헤란로 123 301호");
		assertThat((BigDecimal) quote.get("VOLUME_TON")).isEqualByComparingTo("2.0");
		long moving = ((BigDecimal) quote.get("MOVING_COST")).longValue();
		assertThat(moving).isEqualTo(((BigDecimal) req.get("AI_EST_AMOUNT")).longValue());
		assertThat(((BigDecimal) quote.get("DEPOSIT_AMOUNT")).longValue()).isEqualTo(moving / 10);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE_ITEM WHERE QUOTE_ID = ?", Integer.class, quote.get("QUOTE_ID")))
				.isEqualTo(4);
	}

	@Test
	void undecidedDateUsesRangeMiddle() throws Exception {
		uploadAndAnalyze();
		mvc.perform(post("/quote").session(session).contentType(MediaType.APPLICATION_JSON).content(form(null)))
				.andExpect(status().isOk());
		Map<String, Object> row = jdbc.queryForMap("SELECT R.MOVE_DATE, R.PREFER_TIME, R.AI_EST_MIN, R.AI_EST_MAX, Q.MOVING_COST "
				+ "FROM TBL_MOVE_REQUEST R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE R.MEMBER_ID = ?", memberId);
		assertThat(row.get("MOVE_DATE")).isNull();
		assertThat(row.get("PREFER_TIME")).isNull();
		long mid = (((BigDecimal) row.get("AI_EST_MIN")).longValue() + ((BigDecimal) row.get("AI_EST_MAX")).longValue()) / 2;
		assertThat(((BigDecimal) row.get("MOVING_COST")).longValue()).isEqualTo(Math.round(mid / 10000.0) * 10000);
	}

	@Test
	void rejectsInvalidInput() throws Exception {
		// 영상 없이 제출
		mvc.perform(post("/quote").session(session).contentType(MediaType.APPLICATION_JSON).content(form(null)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("영상을 먼저 올려 주세요."));
		// 동영상이 아닌 파일
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.txt", "text/plain", new byte[] { 1 }))
				.session(session)).andExpect(status().isBadRequest());
		uploadAndAnalyze();
		// 지난 날짜
		mvc.perform(post("/quote").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(form(LocalDate.now().minusDays(1).toString())))
				.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_MOVE_REQUEST WHERE MEMBER_ID = ?", Integer.class, memberId)).isZero();
	}

	@Test
	void addressAndCalendar() throws Exception {
		mvc.perform(get("/quote/address").session(session).param("q", "테헤란로"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].road").value("서울 강남구 테헤란로 123"));
		mvc.perform(get("/quote/calendar").session(session).param("month", "2026-12"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.closed").isArray());
		mvc.perform(get("/quote").session(session)).andExpect(status().isOk());
	}
}
