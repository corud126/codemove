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

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import com.codemove.domain.ClosedDay;
import com.codemove.member.LoginMember;
import com.codemove.schedule.AvailabilityService;
import com.codemove.schedule.ScheduleMapper;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

/** 관리자 예약 관리 SCR-A-011: 목록 → [결제 완료] · [예약 취소] · [일정 변경](예약 가능일 확인). 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class AdminReservationFlowTest {

	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yy.MM.dd");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AvailabilityService availabilityService;

	@Autowired
	private ScheduleMapper scheduleMapper;

	private MockHttpSession admin;
	private MockHttpSession user;
	private Long memberId;
	private String memberPhone;

	@BeforeEach
	void setUp() {
		memberPhone = String.format("010-6%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '예약관리테스트', ?, 'Y', SYSDATE)",
				memberId, memberPhone);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "예약관리테스트", memberPhone));

		Long adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '테스트관리자', ?)",
				adminId, "r" + adminId, String.format("010-3%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000)));
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "r" + adminId, "테스트관리자", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);
	}

	/** from 일 뒤부터 예약 가능한 첫날 (휴무·마감 제외) */
	private LocalDate availableDate(int from, LocalDate except) {
		for (int i = from; i < from + 60; i++) {
			LocalDate d = LocalDate.now().plusDays(i);
			if (!d.equals(except) && availabilityService.isAvailable(d)) {
				return d;
			}
		}
		throw new IllegalStateException("예약 가능한 날이 없어요");
	}

	/** 견적 요청 → (승인 상태로) → 고객 견적 확정 → 예약 ID */
	private Long reserve(LocalDate date) throws Exception {
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(user))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(user)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"소파","size":"중형","count":1,"aiIndex":2,"review":false}],
				 "house":"사무실","pack":"반포장","from":{"road":"서울 마포구 월드컵로 10","detail":""},
				 "to":{"road":"서울 서대문구 연희로 20","detail":"2층"},"date":"%s","time":"오후"}
				""".formatted(date))).andExpect(status().isOk());
		Long quoteId = jdbc.queryForObject("SELECT MAX(Q.QUOTE_ID) FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, memberId);
		jdbc.update("""
				UPDATE TBL_QUOTE SET STATUS = '02', APPROVED_AT = SYSDATE, UPDATED_AT = SYSDATE, EXPIRES_AT = SYSDATE + 3, WORK_TIME = '13:00',
				       TOTAL_AMOUNT = 1320000, DEPOSIT_AMOUNT = 132000, BALANCE_AMOUNT = 1188000
				 WHERE QUOTE_ID = ?""", quoteId);
		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, quoteId)
				.toLocalDateTime().toString();
		mvc.perform(post("/estimates/" + quoteId + "/confirm").session(user).param("updatedAt", updatedAt)).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT R.RESERVATION_ID FROM TBL_RESERVATION R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE Q.QUOTE_ID = ?",
				Long.class, quoteId);
	}

	private String schedule(String date, String time) {
		return "{\"date\":\"" + date + "\",\"time\":\"" + time + "\"}";
	}

	@Test
	void listAndPayment() throws Exception {
		LocalDate date = availableDate(10, null);
		Long id = reserve(date);
		String no = jdbc.queryForObject("SELECT RESERVATION_NO FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id);
		String quoteNo = jdbc.queryForObject("SELECT Q.QUOTE_NO FROM TBL_QUOTE Q JOIN TBL_RESERVATION R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.RESERVATION_ID = ?",
				String.class, id);

		// 목록: 실데이터 행 (이름·휴대폰·이사일·결제 대기·계약금·번호)
		mvc.perform(get("/admin/reservations").session(admin))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-live=\"true\"")))
				.andExpect(content().string(containsString("data-row=\"" + id + "\"")))
				.andExpect(content().string(containsString("예약관리테스트")))
				.andExpect(content().string(containsString(memberPhone)))
				.andExpect(content().string(containsString("data-moving-date=\"" + date.format(YMD) + "\"")))
				.andExpect(content().string(containsString("data-moving-time=\"13:00\"")))
				.andExpect(content().string(containsString("data-service=\"기업이사\"")))
				.andExpect(content().string(containsString("data-pack=\"반포장\"")))
				.andExpect(content().string(containsString("data-deposit=\"132000\"")))
				.andExpect(content().string(containsString("data-rsv-no=\"" + no + "\"")))
				.andExpect(content().string(containsString("data-quote-no=\"" + quoteNo + "\"")));

		// [결제 완료] → 결제 완료 + 결제일, 두 번은 안 됨
		mvc.perform(post("/admin/reservations/" + id + "/paid").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.paidAt").value(LocalDate.now().format(YMD)));
		Map<String, Object> row = jdbc.queryForMap("SELECT PAY_STATUS, DEPOSIT_PAID_AT FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", id);
		assertThat(row.get("PAY_STATUS")).isEqualTo("02");
		assertThat(row.get("DEPOSIT_PAID_AT")).isNotNull();
		mvc.perform(post("/admin/reservations/" + id + "/paid").session(admin)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").exists());
		mvc.perform(get("/admin/reservations").session(admin))
				.andExpect(content().string(containsString("data-payment=\"결제 완료\"")));

		// 결제 완료 후 취소: 결제 상태는 그대로 (환불은 범위 밖)
		mvc.perform(post("/admin/reservations/" + id + "/cancel").session(admin)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT PAY_STATUS FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id)).isEqualTo("02");

		// 없는 예약 · 로그인 안 한 관리자
		mvc.perform(post("/admin/reservations/0/paid").session(admin)).andExpect(status().isBadRequest());
		mvc.perform(get("/admin/reservations")).andExpect(redirectedUrl("/admin/login"));
	}

	@Test
	void adminCancel() throws Exception {
		Long id = reserve(availableDate(10, null));
		mvc.perform(post("/admin/reservations/" + id + "/cancel").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cancelledAt").value(LocalDate.now().format(YMD)));
		Map<String, Object> row = jdbc.queryForMap("SELECT STATUS, PAY_STATUS, CANCELLED_BY, CANCELLED_AT FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", id);
		assertThat(row.get("STATUS")).isEqualTo("03");
		assertThat(row.get("PAY_STATUS")).isEqualTo("03");
		assertThat(row.get("CANCELLED_BY")).isEqualTo("02");
		assertThat(row.get("CANCELLED_AT")).isNotNull();

		// 취소된 예약은 다시 취소 · 결제 · 일정 변경 불가
		mvc.perform(post("/admin/reservations/" + id + "/cancel").session(admin)).andExpect(status().isConflict());
		mvc.perform(post("/admin/reservations/" + id + "/paid").session(admin)).andExpect(status().isConflict());
		mvc.perform(post("/admin/reservations/" + id + "/schedule").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(schedule(availableDate(20, null).format(YMD), "10:00"))).andExpect(status().isConflict());

		mvc.perform(get("/admin/reservations").session(admin))
				.andExpect(content().string(containsString("data-cancelled-by=\"관리자\"")))
				.andExpect(content().string(containsString("data-payment=\"결제 취소\"")));
		// 고객 화면: 예약내역에 취소로
		mvc.perform(get("/more/reservations").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("past-booking cancelled")));
	}

	@Test
	void reschedule() throws Exception {
		LocalDate date = availableDate(10, null);
		Long id = reserve(date);
		String no = jdbc.queryForObject("SELECT RESERVATION_NO FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id);
		String url = "/admin/reservations/" + id + "/schedule";

		// 잘못된 값
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(schedule(date.format(YMD), "09:00")))
				.andExpect(status().isBadRequest());
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(schedule("26.02.30", "10:00")))
				.andExpect(status().isBadRequest());
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(schedule(LocalDate.now().format(YMD), "10:00")))
				.andExpect(status().isBadRequest());

		// 같은 날 시간만 변경
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(schedule(date.format(YMD), "08:00")))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT MOVE_TIME FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", String.class, id)).isEqualTo("08:00");

		// 다른 날로 변경: 예약 쪽만 바뀌고 견적서 작업일 · 예약 번호는 그대로
		LocalDate next = availableDate(20, date);
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(schedule(next.format(YMD), "17:00")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
		Map<String, Object> row = jdbc.queryForMap("SELECT MOVE_DATE, MOVE_TIME, RESERVATION_NO FROM TBL_RESERVATION WHERE RESERVATION_ID = ?", id);
		assertThat(((Timestamp) row.get("MOVE_DATE")).toLocalDateTime().toLocalDate()).isEqualTo(next);
		assertThat(row.get("MOVE_TIME")).isEqualTo("17:00");
		assertThat(row.get("RESERVATION_NO")).isEqualTo(no);
		assertThat(jdbc.queryForObject("SELECT Q.WORK_DATE FROM TBL_QUOTE Q JOIN TBL_RESERVATION R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.RESERVATION_ID = ?",
				Date.class, id).toLocalDate()).isEqualTo(date);

		// 임시 휴무일로는 바꿀 수 없다
		LocalDate closed = availableDate(30, next);
		// (매퍼로 넣어야 같은 트랜잭션의 MyBatis 조회 캐시가 비워진다)
		ClosedDay day = new ClosedDay();
		day.setClosedDate(closed);
		scheduleMapper.insertClosedDay(day);
		mvc.perform(post(url).session(admin).contentType(MediaType.APPLICATION_JSON).content(schedule(closed.format(YMD), "10:00")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("휴무일")));

		// 고객 화면에 바뀐 일정
		mvc.perform(get("/move").param("tab", "booking").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("오후 5:00")));
	}

	@Test
	void previews() throws Exception {
		for (String state : new String[] { "", "detail", "paid", "cancel-filter", "search", "empty", "cancel-detail", "done-detail",
				"reschedule", "cancel-confirm" }) {
			mvc.perform(get("/admin/reservations").param("state", state).session(admin)).andExpect(status().isOk());
			mvc.perform(get("/dev/admin/reservation/list").param("state", state).session(admin))
					.andExpect(status().isOk()).andExpect(content().string(containsString("정하윤")));
		}
	}
}
