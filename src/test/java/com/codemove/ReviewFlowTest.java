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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.admin.LoginAdmin;
import com.codemove.member.LoginMember;
import com.codemove.review.HomeReview;
import com.codemove.review.ReviewService;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

/** 후기: 작성(별점 · 내용 · 사진) · 검증 · 내 후기 목록 · 관리자 답변 등록/수정/삭제 · 홈 최근 후기 · 미리보기. 팀 DB 사용, 테스트마다 롤백 */
@SpringBootTest(properties = { "codemove.quote.upload-dir=build/test-uploads", "codemove.review.upload-dir=build/test-uploads/reviews" })
@AutoConfigureMockMvc
@Transactional
class ReviewFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ReviewService reviewService;

	private MockHttpSession user;
	private MockHttpSession admin;
	private Long memberId;
	private Long adminId;

	@BeforeEach
	void setUp() {
		memberId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		String phone = randomPhone("010-6");
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '후기테스트', ?, 'Y', SYSDATE)",
				memberId, phone);
		user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "후기테스트", phone));

		adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '후기관리자', ?)",
				adminId, "r" + adminId, randomPhone("010-3"));
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "r" + adminId, "후기관리자", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);
	}

	private static String randomPhone(String prefix) {
		return String.format("%s%03d-%04d", prefix, ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
	}

	/** 견적 요청 → (승인 상태로) → 견적 확정 → 예약 ID (ReservationFlowTest 와 같은 흐름) */
	private Long reserve(MockHttpSession session, Long member, LocalDate date) throws Exception {
		mvc.perform(multipart("/quote/video").file(new MockMultipartFile("video", "a.mp4", "video/mp4", new byte[] { 1 })).session(session))
				.andExpect(status().isOk());
		mvc.perform(post("/quote/analyze").session(session)).andExpect(status().isOk());
		mvc.perform(post("/quote").session(session).contentType(MediaType.APPLICATION_JSON).content("""
				{"items":[{"name":"소파","size":"중형","count":1,"aiIndex":2,"review":false}],
				 "house":"빌라","pack":"반포장","from":{"road":"서울 마포구 월드컵로 10","detail":""},
				 "to":{"road":"서울 서대문구 연희로 20","detail":"2층"},"date":"%s","time":"오후"}
				""".formatted(date))).andExpect(status().isOk());
		Long quoteId = jdbc.queryForObject("SELECT MAX(Q.QUOTE_ID) FROM TBL_QUOTE Q JOIN TBL_MOVE_REQUEST R ON R.REQUEST_ID = Q.REQUEST_ID WHERE R.MEMBER_ID = ?",
				Long.class, member);
		jdbc.update("UPDATE TBL_QUOTE SET STATUS = '02', APPROVED_AT = SYSDATE, UPDATED_AT = SYSDATE, EXPIRES_AT = SYSDATE + 3, WORK_TIME = '13:00' WHERE QUOTE_ID = ?",
				quoteId);
		String updatedAt = jdbc.queryForObject("SELECT UPDATED_AT FROM TBL_QUOTE WHERE QUOTE_ID = ?", Timestamp.class, quoteId)
				.toLocalDateTime().toString();
		mvc.perform(post("/estimates/" + quoteId + "/confirm").session(session).param("updatedAt", updatedAt)).andExpect(status().isOk());
		return jdbc.queryForObject("SELECT R.RESERVATION_ID FROM TBL_RESERVATION R JOIN TBL_QUOTE Q ON Q.REQUEST_ID = R.REQUEST_ID WHERE Q.QUOTE_ID = ?",
				Long.class, quoteId);
	}

	/** 예약 후 이사일을 어제로 → 화면을 열면 이사 완료(02) */
	private Long completedMove() throws Exception {
		Long id = reserve(user, memberId, LocalDate.now().plusDays(5));
		jdbc.update("UPDATE TBL_RESERVATION SET MOVE_DATE = TRUNC(SYSDATE) - 1 WHERE RESERVATION_ID = ?", id);
		return id;
	}

	private static MockMultipartFile photo(String name) {
		return new MockMultipartFile("photos", name, "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G' });
	}

	private MockMultipartHttpServletRequestBuilder write(Long reservationId, String rating, String content) {
		MockMultipartHttpServletRequestBuilder b = multipart("/more/reviews");
		b.param("reservation", String.valueOf(reservationId)).param("rating", rating).param("content", content).session(user);
		return b;
	}

	private Long reviewOf(Long reservationId) {
		return jdbc.queryForObject("SELECT REVIEW_ID FROM TBL_REVIEW WHERE RESERVATION_ID = ?", Long.class, reservationId);
	}

	@Test
	void writeReviewWithPhotosAndList() throws Exception {
		Long id = completedMove();

		// 후기 목록 위 '후기 작성' 카드 · 작성 화면
		mvc.perform(get("/more/reviews").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/more/reviews/new?reservation=" + id)))
				.andExpect(content().string(containsString("마포구")));
		mvc.perform(get("/more/reviews/new").param("reservation", String.valueOf(id)).session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("마포구 → 서대문구 · 빌라 반포장 · ")))
				.andExpect(content().string(containsString("data-live=\"true\"")));

		mvc.perform(write(id, "4", "  기사님들이 친절하시고 짐도 꼼꼼하게 옮겨주셨어요.  ").file(photo("a.png")).file(photo("b.png")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.redirect").value("/more/reviews?state=done"));

		Long reviewId = reviewOf(id);
		Map<String, Object> row = jdbc.queryForMap("SELECT RATING, CONTENT, REPLY_CONTENT FROM TBL_REVIEW WHERE REVIEW_ID = ?", reviewId);
		assertThat(((Number) row.get("RATING")).intValue()).isEqualTo(4);
		assertThat(row.get("CONTENT")).isEqualTo("기사님들이 친절하시고 짐도 꼼꼼하게 옮겨주셨어요.");
		assertThat(row.get("REPLY_CONTENT")).isNull();
		List<Map<String, Object>> photos = jdbc.queryForList(
				"SELECT PHOTO_ID, FILE_PATH, SORT_ORDER FROM TBL_REVIEW_PHOTO WHERE REVIEW_ID = ? ORDER BY SORT_ORDER", reviewId);
		assertThat(photos).hasSize(2);
		assertThat(((Number) photos.get(0).get("SORT_ORDER")).intValue()).isEqualTo(1);
		assertThat(((Number) photos.get(1).get("SORT_ORDER")).intValue()).isEqualTo(2);
		String path = (String) photos.get(0).get("FILE_PATH");
		assertThat(path).contains("test-uploads").endsWith(".png");
		assertThat(Files.exists(Path.of(path))).isTrue();
		Long photoId = ((Number) photos.get(0).get("PHOTO_ID")).longValue();

		// 내가 쓴 후기: 방금 쓴 후기 · 사진, 작성 카드는 사라짐
		mvc.perform(get("/more/reviews").param("state", "done").session(user))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("기사님들이 친절하시고 짐도 꼼꼼하게 옮겨주셨어요.")))
				.andExpect(content().string(containsString("★★★★☆")))
				.andExpect(content().string(containsString("방금 전")))
				.andExpect(content().string(containsString("빌라 · 반포장")))
				.andExpect(content().string(containsString("/more/reviews/photos/" + photoId)))
				.andExpect(content().string(containsString("후기가 등록되었어요.")))
				.andExpect(content().string(not(containsString("/more/reviews/new?reservation=" + id))))
				.andExpect(content().string(not(containsString("사장님 답변"))));
		mvc.perform(get("/more/reviews/photos/" + photoId).session(user))
				.andExpect(status().isOk()).andExpect(content().contentType("image/png"));

		// 다른 회원은 사진을 볼 수 없다
		MockHttpSession other = new MockHttpSession();
		other.setAttribute(HomeController.LOGIN_KEY, new LoginMember(-1L, "남", "010-0000-0000"));
		mvc.perform(get("/more/reviews/photos/" + photoId).session(other)).andExpect(status().isNotFound());

		// 이미 쓴 예약: 작성 화면은 목록으로, 다시 등록은 거절
		mvc.perform(get("/more/reviews/new").param("reservation", String.valueOf(id)).session(user)).andExpect(redirectedUrl("/more/reviews"));
		mvc.perform(get("/more/reviews/new").session(user)).andExpect(redirectedUrl("/more/reviews"));
		mvc.perform(write(id, "5", "또 씁니다")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("이미 후기를 쓴 이사예요."));

		// 내 이사 완료 탭: 리뷰 작성 완료
		mvc.perform(get("/move").param("tab", "done").session(user))
				.andExpect(status().isOk()).andExpect(content().string(containsString("리뷰 작성 완료")));

		// 홈 최근 후기 (이름 가림 · 이용 회차 · 유형)
		List<HomeReview> home = reviewService.latestForHome(50);
		assertThat(home).anySatisfy(h -> {
			assertThat(h.content()).isEqualTo("기사님들이 친절하시고 짐도 꼼꼼하게 옮겨주셨어요.");
			assertThat(h.name()).isEqualTo("후***트");
			assertThat(h.meta()).isEqualTo("이용 1회차 · 오늘");
			assertThat(h.type()).isEqualTo("빌라(반포장)");
			assertThat(h.stars()).isEqualTo("★★★★☆");
		});
		assertThat(reviewService.latestForHome(0)).isEmpty();
	}

	@Test
	void validation() throws Exception {
		Long id = completedMove();
		mvc.perform(write(id, "0", "내용")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("별점을 선택해 주세요."));
		mvc.perform(write(id, "6", "내용")).andExpect(status().isBadRequest());
		mvc.perform(multipart("/more/reviews").param("reservation", String.valueOf(id)).param("content", "내용").session(user))
				.andExpect(status().isBadRequest());
		mvc.perform(write(id, "5", "   ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("후기 내용을 입력해 주세요."));
		mvc.perform(write(id, "5", "가".repeat(501))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("후기는 500자까지 입력할 수 있어요."));
		MockMultipartHttpServletRequestBuilder six = write(id, "5", "사진 여섯 장");
		for (int i = 0; i < 6; i++) {
			six.file(photo(i + ".png"));
		}
		mvc.perform(six).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("사진은 5장까지 올릴 수 있어요."));
		mvc.perform(write(id, "5", "파일").file(new MockMultipartFile("photos", "a.pdf", "application/pdf", new byte[] { 1 })))
				.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_REVIEW WHERE RESERVATION_ID = ?", Integer.class, id)).isZero();

		// 500자 · 사진 5장은 된다
		MockMultipartHttpServletRequestBuilder five = write(id, "5", "가".repeat(500));
		for (int i = 0; i < 5; i++) {
			five.file(photo(i + ".png"));
		}
		mvc.perform(five).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_REVIEW_PHOTO WHERE REVIEW_ID = ?", Integer.class, reviewOf(id))).isEqualTo(5);

		// 이사 전(예약 확정) 예약은 쓸 수 없다
		Long upcoming = reserve(user, memberId, LocalDate.now().plusDays(7));
		mvc.perform(write(upcoming, "5", "아직 이사 전")).andExpect(status().isConflict());
		mvc.perform(get("/more/reviews/new").param("reservation", String.valueOf(upcoming)).session(user))
				.andExpect(redirectedUrl("/more/reviews"));

		// 다른 회원의 예약 · 없는 예약
		Long otherId = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		String phone = randomPhone("010-6");
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, '남의회원', ?, 'Y', SYSDATE)",
				otherId, phone);
		MockHttpSession other = new MockHttpSession();
		other.setAttribute(HomeController.LOGIN_KEY, new LoginMember(otherId, "남의회원", phone));
		Long othersMove = reserve(other, otherId, LocalDate.now().plusDays(5));
		jdbc.update("UPDATE TBL_RESERVATION SET MOVE_DATE = TRUNC(SYSDATE) - 1 WHERE RESERVATION_ID = ?", othersMove);
		mvc.perform(write(othersMove, "5", "남의 이사")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("예약을 찾을 수 없어요."));
		mvc.perform(get("/more/reviews/new").param("reservation", String.valueOf(othersMove)).session(user))
				.andExpect(redirectedUrl("/more/reviews"));
		mvc.perform(write(-1L, "5", "없는 예약")).andExpect(status().isBadRequest());
	}

	@Test
	void adminReplyAddEditDelete() throws Exception {
		Long id = completedMove();
		mvc.perform(write(id, "3", "일부 가구에 흠집이 생겼어요.").file(photo("a.png"))).andExpect(status().isOk());
		Long reviewId = reviewOf(id);
		Long photoId = jdbc.queryForObject("SELECT PHOTO_ID FROM TBL_REVIEW_PHOTO WHERE REVIEW_ID = ?", Long.class, reviewId);

		mvc.perform(get("/admin/reviews").session(admin))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-review=\"" + reviewId + "\"")))
				.andExpect(content().string(containsString("일부 가구에 흠집이 생겼어요.")))
				.andExpect(content().string(containsString("후기테스트")))
				.andExpect(content().string(containsString("가정이사 · 반포장 · 마포구 → 서대문구")))
				.andExpect(content().string(containsString("/admin/reviews/photos/" + photoId)))
				.andExpect(content().string(containsString("data-live=\"true\"")));
		mvc.perform(get("/admin/reviews/photos/" + photoId).session(admin)).andExpect(status().isOk());

		// 답변 등록
		mvc.perform(post("/admin/reviews/" + reviewId + "/reply").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"  불편을 드려 죄송합니다. 바로 연락드리겠습니다.  \"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.repliedAt").isNotEmpty());
		Map<String, Object> row = jdbc.queryForMap("SELECT REPLY_CONTENT, REPLIED_AT, ADMIN_ID FROM TBL_REVIEW WHERE REVIEW_ID = ?", reviewId);
		assertThat(row.get("REPLY_CONTENT")).isEqualTo("불편을 드려 죄송합니다. 바로 연락드리겠습니다.");
		assertThat(row.get("REPLIED_AT")).isNotNull();
		assertThat(((Number) row.get("ADMIN_ID")).longValue()).isEqualTo(adminId);
		mvc.perform(get("/more/reviews").session(user))
				.andExpect(content().string(containsString("사장님 답변")))
				.andExpect(content().string(containsString("불편을 드려 죄송합니다. 바로 연락드리겠습니다.")));

		// 답변 수정 · 검증
		mvc.perform(post("/admin/reviews/" + reviewId + "/reply").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"사후 처리 완료했습니다.\"}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT REPLY_CONTENT FROM TBL_REVIEW WHERE REVIEW_ID = ?", String.class, reviewId))
				.isEqualTo("사후 처리 완료했습니다.");
		mvc.perform(post("/admin/reviews/" + reviewId + "/reply").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"" + "가".repeat(501) + "\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/admin/reviews/" + reviewId + "/reply").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\" \"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/admin/reviews/0/reply").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"없는 리뷰\"}")).andExpect(status().isBadRequest());

		// 답변 삭제 → 답변 대기
		mvc.perform(post("/admin/reviews/" + reviewId + "/reply/delete").session(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
		row = jdbc.queryForMap("SELECT REPLY_CONTENT, REPLIED_AT, ADMIN_ID FROM TBL_REVIEW WHERE REVIEW_ID = ?", reviewId);
		assertThat(row.get("REPLY_CONTENT")).isNull();
		assertThat(row.get("REPLIED_AT")).isNull();
		assertThat(row.get("ADMIN_ID")).isNull();
		mvc.perform(get("/more/reviews").session(user)).andExpect(content().string(not(containsString("사장님 답변"))));
		mvc.perform(post("/admin/reviews/0/reply/delete").session(admin)).andExpect(status().isBadRequest());

		// 로그인하지 않으면 관리자 화면 · 사진에 접근할 수 없다
		mvc.perform(get("/admin/reviews/photos/" + photoId)).andExpect(status().is3xxRedirection());
	}

	@Test
	void previewsRender() throws Exception {
		mvc.perform(get("/dev/user/more/reviews")).andExpect(status().isOk())
				.andExpect(content().string(containsString("송파구 → 성동구")))
				.andExpect(content().string(containsString("사장님 답변")));
		mvc.perform(get("/dev/user/more/reviews").param("state", "done")).andExpect(status().isOk())
				.andExpect(content().string(containsString("submittedReview")));
		mvc.perform(get("/dev/user/more/review-new")).andExpect(status().isOk())
				.andExpect(content().string(containsString("강남구 → 서초구 · 원룸 일반포장 · 3월 18일")));
		mvc.perform(get("/dev/user/more/review-new").param("state", "filled")).andExpect(status().isOk())
				.andExpect(content().string(containsString("39/500")));
		for (String state : new String[] { "", "reply", "edit-reply", "filter", "empty", "pending", "filter-menu", "answer-delete" }) {
			mvc.perform(get("/dev/admin/review/list").param("state", state)).andExpect(status().isOk())
					.andExpect(content().string(containsString("김명품")))
					.andExpect(content().string(containsString(">4.5<")));
		}
		// 후기가 없는 회원: 작성 카드 없이 빈 목록
		mvc.perform(get("/more/reviews").session(user)).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("review-prompt\""))));
	}
}
