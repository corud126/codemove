package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.codemove.common.holiday.Holiday;
import com.codemove.common.holiday.HolidayProvider;
import com.codemove.schedule.AvailabilityService;
import com.codemove.schedule.MonthAvailability;
import com.codemove.schedule.ScheduleService;
import com.codemove.web.admin.AdminAuthController;

/**
 * 일정 관리(예약 설정 · 임시 휴무) · 예약 가능 날짜 · 예약 일정 달력 (팀 DB 사용, 테스트마다 롤백).
 * 공휴일은 API 키를 비워 내장 목록으로 확인한다. 날짜는 다른 데이터와 겹치지 않게 2027년 3월을 쓴다.
 */
@SpringBootTest(properties = "codemove.holiday.service-key=")
@AutoConfigureMockMvc
@Transactional
class ScheduleFlowTest {

	/** 2027-03-01 삼일절(월) */
	private static final LocalDate HOLIDAY = LocalDate.of(2027, 3, 1);
	/** 2027-03-07 일요일 */
	private static final LocalDate SUNDAY = LocalDate.of(2027, 3, 7);
	private static final LocalDate DAY_A = LocalDate.of(2027, 3, 10);
	private static final LocalDate DAY_B = LocalDate.of(2027, 3, 11);
	private static final LocalDate DAY_C = LocalDate.of(2027, 3, 12);
	private static final YearMonth MARCH = YearMonth.of(2027, 3);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ScheduleService scheduleService;

	@Autowired
	private AvailabilityService availabilityService;

	@Autowired
	private HolidayProvider holidayProvider;

	private MockHttpSession session;
	private long adminId;
	private long memberId;

	@BeforeEach
	void setUp() {
		adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		String loginId = "s" + ThreadLocalRandom.current().nextInt(1_000_000);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '테스트', '010-0000-0000')",
				adminId, loginId);
		session = new MockHttpSession();
		session.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, loginId, "테스트", "01"));
		session.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);

		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		String phone = String.format("010-8%03d-%04d", ThreadLocalRandom.current().nextInt(1000),
				ThreadLocalRandom.current().nextInt(10000));
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, TERMS_AGREED_AT) VALUES (?, '일정테스트', ?, SYSDATE)",
				memberId, phone);

		// 테스트 날짜에 이미 지정된 임시 휴무가 있으면 지우고 시작 (롤백됨)
		for (LocalDate d : List.of(DAY_A, DAY_B, DAY_C)) {
			scheduleService.removeClosedDay(d);
		}
	}

	/** 견적 요청(FK) + 예약 1건 */
	private long insertReservation(LocalDate moveDate, String time, String status) {
		long requestId = jdbc.queryForObject("SELECT SEQ_MOVE_REQUEST.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MOVE_REQUEST (REQUEST_ID, MEMBER_ID, HOUSE_TYPE, PACK_TYPE, MOVE_DATE, FROM_ADDRESS, FROM_DETAIL, TO_ADDRESS) "
				+ "VALUES (?, ?, '01', '01', ?, '서울 마포구 월드컵로 212', '101호', '서울 용산구 한강대로 405')",
				requestId, memberId, Date.valueOf(moveDate));
		long reservationId = jdbc.queryForObject("SELECT SEQ_RESERVATION.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_RESERVATION (RESERVATION_ID, RESERVATION_NO, REQUEST_ID, STATUS, PAY_STATUS, MOVE_DATE, MOVE_TIME) "
				+ "VALUES (?, ?, ?, ?, '01', ?, ?)",
				reservationId, "ME-T" + reservationId, requestId, status, Date.valueOf(moveDate), time);
		return reservationId;
	}

	private int existingConfirmed(LocalDate date) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM TBL_RESERVATION WHERE STATUS = '01' AND TRUNC(MOVE_DATE) = ?",
				Integer.class, Date.valueOf(date));
	}

	@Test
	void saveSettings() throws Exception {
		mvc.perform(post("/admin/schedule/setting").session(session)
				.param("daily", "5").param("weekdays", "1,0").param("holiday", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.daily").value(5))
				.andExpect(jsonPath("$.weekdays[0]").value(0))
				.andExpect(jsonPath("$.weekdays[1]").value(1))
				.andExpect(jsonPath("$.holiday").value(true));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_RESERVATION_SETTING", Integer.class)).isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT DAILY_CAPACITY, CLOSED_WEEKDAYS, HOLIDAY_CLOSED, ADMIN_ID FROM TBL_RESERVATION_SETTING");
		assertThat(((Number) row.get("DAILY_CAPACITY")).intValue()).isEqualTo(5);
		assertThat(row.get("CLOSED_WEEKDAYS")).isEqualTo("0,1");
		assertThat(row.get("HOLIDAY_CLOSED")).isEqualTo("Y");
		assertThat(((Number) row.get("ADMIN_ID")).longValue()).isEqualTo(adminId);

		// 정기 휴무 해제 → NULL
		mvc.perform(post("/admin/schedule/setting").session(session).param("daily", "3").param("weekdays", "").param("holiday", "false"))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT CLOSED_WEEKDAYS FROM TBL_RESERVATION_SETTING", String.class)).isNull();
		assertThat(scheduleService.setting().holidayClosed()).isFalse();

		mvc.perform(post("/admin/schedule/setting").session(session).param("daily", "11"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
		mvc.perform(post("/admin/schedule/setting").session(session).param("daily", "3").param("weekdays", "7"))
				.andExpect(status().isBadRequest());

		mvc.perform(get("/admin/schedule/settings").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.daily").value(3))
				.andExpect(jsonPath("$.closedDays").isArray());
	}

	@Test
	void closedDaysAddMoveRemove() throws Exception {
		insertReservation(DAY_A, "10:00", "01");
		int bookedA = existingConfirmed(DAY_A);

		mvc.perform(post("/admin/schedule/closed-days").session(session).param("date", DAY_A.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.booked").value(bookedA));
		Map<String, Object> row = jdbc.queryForMap("SELECT ADMIN_ID FROM TBL_CLOSED_DAY WHERE CLOSED_DATE = ?", Date.valueOf(DAY_A));
		assertThat(((Number) row.get("ADMIN_ID")).longValue()).isEqualTo(adminId);

		// 같은 날짜 중복 · 지난 날짜는 막음
		mvc.perform(post("/admin/schedule/closed-days").session(session).param("date", DAY_A.toString()))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/admin/schedule/closed-days").session(session).param("date", LocalDate.now().minusDays(1).toString()))
				.andExpect(status().isBadRequest());

		// 날짜 변경 A → C
		mvc.perform(post("/admin/schedule/closed-days").session(session)
				.param("date", DAY_C.toString()).param("before", DAY_A.toString()))
				.andExpect(status().isOk());
		assertThat(countClosed(DAY_A)).isZero();
		assertThat(countClosed(DAY_C)).isEqualTo(1);

		mvc.perform(post("/admin/schedule/closed-days/delete").session(session).param("date", DAY_C.toString()))
				.andExpect(status().isOk());
		assertThat(countClosed(DAY_C)).isZero();
		mvc.perform(post("/admin/schedule/closed-days/delete").session(session).param("date", DAY_C.toString()))
				.andExpect(status().isNotFound());
	}

	private int countClosed(LocalDate d) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM TBL_CLOSED_DAY WHERE TRUNC(CLOSED_DATE) = ?", Integer.class, Date.valueOf(d));
	}

	@Test
	void availability() {
		scheduleService.saveSetting(2, Set.of(0), true, adminId);
		scheduleService.addClosedDay(DAY_A, null, adminId);

		// 마감: 확정(01) 건수가 하루 최대(2)에 닿게 채움. 이사 완료(02) · 취소(03)는 세지 않음
		for (int i = existingConfirmed(DAY_B); i < 2; i++) {
			insertReservation(DAY_B, "08:00", "01");
		}
		insertReservation(DAY_C, "08:00", "02");
		insertReservation(DAY_C, "10:00", "03");
		int confirmedC = existingConfirmed(DAY_C);
		for (int i = confirmedC; i < 1; i++) {
			insertReservation(DAY_C, "13:00", "01");
		}
		// 휴무일에 예약이 차 있어도 closed 에만
		insertReservation(SUNDAY, "08:00", "01");
		insertReservation(SUNDAY, "10:00", "01");

		MonthAvailability m = availabilityService.month(MARCH);
		assertThat(m.closed()).contains(SUNDAY, HOLIDAY, DAY_A).doesNotContain(DAY_B, DAY_C);
		assertThat(m.full()).contains(DAY_B).doesNotContain(SUNDAY, DAY_A);
		assertThat(m.holidays()).contains(HOLIDAY);
		assertThat(m.closed()).allMatch(d -> YearMonth.from(d).equals(MARCH));

		if (existingConfirmed(DAY_C) < 2) {
			assertThat(m.full()).doesNotContain(DAY_C);
			assertThat(availabilityService.isAvailable(DAY_C)).isTrue();
		}
		assertThat(availabilityService.isAvailable(SUNDAY)).isFalse();
		assertThat(availabilityService.isAvailable(HOLIDAY)).isFalse();
		assertThat(availabilityService.isAvailable(DAY_A)).isFalse();
		assertThat(availabilityService.isAvailable(DAY_B)).isFalse();
		assertThat(availabilityService.isAvailable(LocalDate.now())).isFalse();
		assertThat(availabilityService.isAvailableForConfirm(DAY_B)).isFalse();

		// 공휴일에 쉬기 끄기 → 공휴일은 holidays 에만 · 하루 최대 늘리면 마감 풀림 · 임시 휴무 해제
		scheduleService.saveSetting(10, Set.of(0), false, adminId);
		scheduleService.removeClosedDay(DAY_A);
		MonthAvailability after = availabilityService.month(MARCH);
		assertThat(after.closed()).contains(SUNDAY).doesNotContain(HOLIDAY, DAY_A);
		assertThat(after.holidays()).contains(HOLIDAY);
		if (existingConfirmed(DAY_B) < 10) {
			assertThat(after.full()).doesNotContain(DAY_B);
			assertThat(availabilityService.isAvailable(DAY_B)).isTrue();
		}
		assertThat(availabilityService.isAvailable(HOLIDAY)).isTrue();
	}

	@Test
	void calendarMonth() throws Exception {
		long confirmed = insertReservation(DAY_B, "13:00", "01");
		long moved = insertReservation(DAY_B, "08:00", "02");
		long cancelled = insertReservation(DAY_B, "10:00", "03");

		String json = mvc.perform(get("/admin/schedule/month").session(session).param("ym", "2027-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.holidays[0].date").value("2027-03-01"))
				.andExpect(jsonPath("$.noMove").isArray())
				.andExpect(jsonPath("$.reservations[?(@.id == " + confirmed + ")].status").value("예약 완료"))
				.andExpect(jsonPath("$.reservations[?(@.id == " + confirmed + ")].service").value("원룸이사"))
				.andExpect(jsonPath("$.reservations[?(@.id == " + confirmed + ")].origin").value("서울 마포구 월드컵로 212 101호"))
				.andExpect(jsonPath("$.reservations[?(@.id == " + moved + ")].status").value("이사 완료"))
				.andReturn().getResponse().getContentAsString();
		assertThat(json).doesNotContain("\"id\":" + cancelled + ",");

		mvc.perform(get("/admin/schedule/month").session(session).param("ym", "bad"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/admin/schedule").session(session)).andExpect(status().isOk());
		mvc.perform(get("/admin/schedule/manage").session(session)).andExpect(status().isOk());
	}

	@Test
	void holidaysAndNoMoveDays() {
		assertThat(holidayProvider.holidays(YearMonth.of(2026, 9)).stream().map(Holiday::date))
				.containsExactly(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26));
		assertThat(holidayProvider.isHoliday(LocalDate.of(2027, 2, 9))).isTrue(); // 설날 대체공휴일
		assertThat(holidayProvider.isHoliday(LocalDate.of(2027, 2, 10))).isFalse();

		// 2026 추석(음력 8/15) = 9/25 → 음력 8/9 · 8/10 · 8/19 · 8/20 = 9/19 · 9/20 · 9/29 · 9/30
		List<LocalDate> noMove = holidayProvider.noMoveDays(YearMonth.of(2026, 9));
		assertThat(noMove).contains(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20),
				LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30)).doesNotContain(LocalDate.of(2026, 9, 25));
		// 2027 설날(음력 1/1) = 2/7 (한국 시각 기준) → 1/9 · 1/10 = 2/15 · 2/16
		assertThat(holidayProvider.noMoveDays(YearMonth.of(2027, 2)))
				.contains(LocalDate.of(2027, 2, 15), LocalDate.of(2027, 2, 16)).doesNotContain(LocalDate.of(2027, 2, 14));
	}
}
