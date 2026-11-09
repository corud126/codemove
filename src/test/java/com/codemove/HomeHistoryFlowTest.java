package com.codemove;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import com.codemove.schedule.AvailabilityService;
import com.codemove.web.user.HomeController;

/** 사용자 홈 상태 카드 · 더보기 견적내역 · 예약내역: 견적 없음 → 검토 중 → 견적 도착 → 예약 확정 → 취소 / 이사 완료. 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class HomeHistoryFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AvailabilityService availabilityService;

	private MockHttpSession user;
	private Long memberId;

	@BeforeEach
	void login() {
		String phone = String.format("010-8%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '홈테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "홈테스트", phone));
	}

	private LocalDate availableDate() {
		for (int i = 10; i < 70; i++) {
			LocalDate d = LocalDate.now().plusDays(i);
			if (availabilityService.isAvailable(d)) {
				return d;
			}
		}
		throw new IllegalStateException("예약 가능한 날이 없어요");
	}

	/** 견적 요청 → 견적서 ID (검토 중) */
	private Long request(LocalDate date) throws Exception {
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(user))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(user)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"소파","size":"중형","count":1,"aiIndex":2,"review":false}],
				 "house":"빌라","pack":"반포장","from":{"road":"서울 마포구 월드컵로 10","detail":""},
				 "to":{"road":"서울 서대문구 연희로 20","detail":"2층"},"date":"%s","time":"오후"}
				""".formatted(date))).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT MAX(Q.QUOTE_ID) FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, memberId);
	}

	/** 관리자 승인 상태로 (총액 380,000원, 남은 시간 3일) */
	private void approve(Long quoteId) {
		jdbc.update("""
				UPDATE TBL_QUOTE SET STATUS = '02', APPROVED_AT = SYSDATE, UPDATED_AT = SYSDATE, EXPIRES_AT = SYSDATE + 3, WORK_TIME = '13:00',
				       TOTAL_AMOUNT = 380000, DEPOSIT_AMOUNT = 38000, BALANCE_AMOUNT = 342000
				 WHERE QUOTE_ID = ?""", quoteId);
	}

	/** 고객 견적 확정 → 예약 ID */
	private Long confirm(Long quoteId) throws Exception {
		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, quoteId)
				.toLocalDateTime().toString();
		mvc.perform(post("/estimates/" + quoteId + "/confirm").session(user).param("updatedAt", updatedAt)).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT R.RESERVATION_ID FROM TBL_RESERVATION R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE Q.QUOTE_ID = ?",
				Long.class, quoteId);
	}

	@Test
	void homeAndHistoryFollowQuoteAndReservation() throws Exception {
		// 견적 없음
		mvc.perform(get("/").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("홈테스트님")))
				.andExpect(content().string(containsString("영상으로 견적을 받아보세요")));
		mvc.perform(get("/more/estimates").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("estimate-arrived-card"))))
				.andExpect(content().string(containsString("<div class=\"past-reservation-empty\" data-filter-empty>")));
		mvc.perform(get("/more/reservations").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("upcoming-reservation-card\""))))
				.andExpect(content().string(containsString("<div class=\"past-reservation-empty\" data-filter-empty>")));

		// 검토 중
		LocalDate date = availableDate();
		Long quoteId = request(date);
		mvc.perform(get("/").session(user)).andExpect(content().string(containsString("견적 요청이 접수됐어요")));
		mvc.perform(get("/more/estimates").session(user))
				.andExpect(content().string(containsString("estimate-history-row reviewing")))
				.andExpect(content().string(containsString("마포구 → 서대문구")))
				.andExpect(content().string(containsString("빌라 / 주택 · 반포장")))
				.andExpect(content().string(containsString("/estimates/" + quoteId)));

		// 견적 도착: 홈 카드 · 견적내역 도착 카드(남은 시간 · 이사일 · 금액)
		approve(quoteId);
		mvc.perform(get("/").session(user)).andExpect(content().string(containsString("이사 견적이 준비됐어요")));
		mvc.perform(get("/more/estimates").session(user))
				.andExpect(content().string(containsString("estimate-arrived-card")))
				.andExpect(content().string(containsString("일 ")))
				.andExpect(content().string(containsString("시간 남음")))
				.andExpect(content().string(containsString(date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 (")))
				.andExpect(content().string(containsString(" 이사 · 380,000원")))
				.andExpect(content().string(containsString("estimate-history-row arrived")));

		// 예약 확정: 홈 예약 카드 · 예약내역 다가오는 예약
		Long reservationId = confirm(quoteId);
		String dday = "D-" + ChronoUnit.DAYS.between(LocalDate.now(), date);
		mvc.perform(get("/").session(user))
				.andExpect(content().string(containsString("my-booking-card")))
				.andExpect(content().string(containsString("/reservations/" + reservationId)))
				.andExpect(content().string(containsString(dday)))
				.andExpect(content().string(containsString("오후 1:00")))
				.andExpect(content().string(containsString("월드컵로 10")))
				.andExpect(content().string(containsString("오후 1시에 이사해요")))
				.andExpect(content().string(containsString("홈테스트님")))
				.andExpect(content().string(not(containsString("김코드님"))));
		mvc.perform(get("/more/reservations").session(user))
				.andExpect(content().string(containsString("upcoming-reservation-card")))
				.andExpect(content().string(containsString("/reservations/" + reservationId)))
				.andExpect(content().string(containsString(dday)))
				.andExpect(content().string(containsString("오후 1:00 · 빌라 / 주택 · 반포장")));
		mvc.perform(get("/more/estimates").session(user))
				.andExpect(content().string(containsString("estimate-history-row confirmed")))
				.andExpect(content().string(not(containsString("estimate-arrived-card"))));

		// 고객 취소: 지난 이사에 '취소' (연도별), 홈은 견적 없음 카드로
		mvc.perform(post("/reservations/" + reservationId + "/cancel").session(user)).andExpect(status().isOk());
		mvc.perform(get("/more/reservations").session(user))
				.andExpect(content().string(not(containsString("upcoming-reservation-card\""))))
				.andExpect(content().string(containsString("past-booking cancelled")))
				.andExpect(content().string(containsString(date.getYear() + "년")));
		mvc.perform(get("/").session(user)).andExpect(content().string(containsString("영상으로 견적을 받아보세요")));
	}

	@Test
	void completedMoveAndExpiredQuote() throws Exception {
		Long quoteId = request(availableDate());
		approve(quoteId);
		Long reservationId = confirm(quoteId);
		jdbc.update("UPDATE TBL_RESERVATION SET MOVE_DATE = TRUNC(SYSDATE) - 1 WHERE RESERVATION_ID = ?", reservationId);
		mvc.perform(get("/more/reservations").session(user))
				.andExpect(content().string(containsString("past-booking completed")))
				.andExpect(content().string(containsString("이사 완료")));

		// 만료 시각이 지난 견적은 만료로
		Long second = request(availableDate());
		approve(second);
		jdbc.update("UPDATE TBL_QUOTE SET EXPIRES_AT = SYSDATE - 1 WHERE QUOTE_ID = ?", second);
		mvc.perform(get("/more/estimates").session(user))
				.andExpect(content().string(containsString("estimate-history-row expired")))
				.andExpect(content().string(not(containsString("estimate-arrived-card"))));
		mvc.perform(get("/").session(user)).andExpect(content().string(containsString("영상으로 견적을 받아보세요")));
	}

	@Test
	void previewsAndGuest() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/onboarding"));
		mvc.perform(get("/").param("state", "guest")).andExpect(status().isOk())
				.andExpect(content().string(containsString("로그인하고")));
		// ?state= 미리보기는 실제 상태보다 우선, 예약 확정은 예시 값(김코드님 · D-30)
		mvc.perform(get("/").param("state", "booked").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("김코드님")))
				.andExpect(content().string(containsString("D-30")))
				.andExpect(content().string(containsString("테헤란로 123, 301호")))
				.andExpect(content().string(containsString("3월 18일 오전 10시에 이사해요")));
		mvc.perform(get("/").param("state", "arrived").session(user)).andExpect(content().string(containsString("이사 견적이 준비됐어요")));
		mvc.perform(get("/").param("state", "reviewing").param("mode", "easy").session(user)).andExpect(status().isOk());

		mvc.perform(get("/dev/user/home")).andExpect(status().isOk());
		mvc.perform(get("/dev/user/more/estimates")).andExpect(status().isOk())
				.andExpect(content().string(containsString("2일 13시간 남음")))
				.andExpect(content().string(containsString("강남구 → 송파구")));
		mvc.perform(get("/dev/user/more/estimates").param("state", "filter")).andExpect(status().isOk());
		mvc.perform(get("/dev/user/more/reservations")).andExpect(status().isOk())
				.andExpect(content().string(containsString("2024년")))
				.andExpect(content().string(containsString("강동구 → 광진구")));
		mvc.perform(get("/dev/user/more/reservations").param("state", "filter")).andExpect(status().isOk());
	}
}
