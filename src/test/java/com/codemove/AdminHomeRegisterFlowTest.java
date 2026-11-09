package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.admin.LoginAdmin;
import com.codemove.common.sms.SmsSender;
import com.codemove.dashboard.DashboardService;
import com.codemove.member.LoginMember;
import com.codemove.member.MemberService;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

/**
 * 관리자 홈 집계 · 견적서 등록(사진·PDF) · 원본 파일 · 등록 견적 삭제 · 사용자 휴대폰 번호 변경.
 * 팀 DB 사용, 테스트마다 롤백 (원본 파일은 build/test-uploads)
 */
@SpringBootTest(properties = "codemove.quote.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class AdminHomeRegisterFlowTest {

	private static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3 };
	private static final byte[] PDF = "%PDF-1.4 test".getBytes();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MemberService memberService;

	@MockitoBean
	private SmsSender smsSender;

	private MockHttpSession admin;
	private Long adminId;
	private Long memberId;
	private String memberPhone;

	/** 다른 데이터와 겹치지 않는 번호 (010-6XXX-XXXX) */
	private static String randomPhone() {
		return String.format("010-6%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000));
	}

	private Long insertMember(String name, String phone) {
		Long id = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, ?, ?, 'Y', SYSDATE)",
				id, name, phone);
		return id;
	}

	@BeforeEach
	void setUp() {
		memberPhone = randomPhone();
		memberId = insertMember("등록테스트", memberPhone);
		adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '등록관리자', ?)",
				adminId, "r" + adminId, String.format("010-5%03d-%04d", ThreadLocalRandom.current().nextInt(1000), ThreadLocalRandom.current().nextInt(10000)));
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "r" + adminId, "등록관리자", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);
	}

	/* ---------- 관리자 홈 ---------- */

	@Test
	@SuppressWarnings("unchecked")
	void dashboardShowsDbCounts() throws Exception {
		MvcResult result = mvc.perform(get("/admin").session(admin))
				.andExpect(status().isOk()).andExpect(content().string(containsString("오늘의 현황"))).andReturn();
		Map<String, Object> model = result.getModelAndView().getModel();
		List<DashboardService.Stat> stats = (List<DashboardService.Stat>) model.get("stats");
		assertThat(stats).extracting(DashboardService.Stat::label).containsExactly("전체 예약 신청", "리뷰 등록", "견적 검토", "예약 완료");
		assertThat(stats.get(0).value()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_RESERVATION", Integer.class));
		assertThat(stats.get(1).value()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_REVIEW", Integer.class));
		assertThat(stats.get(2).value()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE WHERE STATUS = '01'", Integer.class));
		assertThat(stats.get(3).value()).isEqualTo(
				jdbc.queryForObject("SELECT COUNT(*) FROM TBL_RESERVATION WHERE STATUS IN ('01', '02')", Integer.class));
		assertThat(((DashboardService.Chart) model.get("chart")).points()).hasSize(7);
		assertThat((List<?>) model.get("recentReservations")).hasSizeLessThanOrEqualTo(3);

		// 견적 검토가 하나 늘면 카드 숫자도 는다 (PDF 견적 검토로 등록)
		int before = stats.get(2).value();
		registerPdf("홈카운트", null, "01");
		List<DashboardService.Stat> after = (List<DashboardService.Stat>) mvc.perform(get("/admin").session(admin)).andReturn()
				.getModelAndView().getModel().get("stats");
		assertThat(after.get(2).value()).isEqualTo(before + 1);
	}

	@Test
	void chartPathFollowsCounts() {
		DashboardService.Chart flat = DashboardService.chart(new int[7]);
		assertThat(flat.points()).allMatch(p -> p.endsWith(",91"));
		DashboardService.Chart peak = DashboardService.chart(new int[] { 0, 0, 4, 0, 2, 0, 0 });
		assertThat(peak.points().get(2)).isEqualTo("116.7,26");
		assertThat(peak.points().get(4)).isEqualTo("223.3,58.5");
		assertThat(peak.area()).startsWith(peak.line()).endsWith("L330 106 L10 106Z");
	}

	/* ---------- 견적서 등록 ---------- */

	@Test
	@SuppressWarnings("unchecked")
	void photoRegisterLinkedToMember() throws Exception {
		mvc.perform(get("/admin/estimates/new").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("등록테스트")));
		// 첨부 전에는 다음 단계로 갈 수 없다
		mvc.perform(get("/admin/estimates/new").param("state", "review").session(admin)).andExpect(redirectedUrl("/admin/estimates/new"));
		// 사진·PDF 가 아니면 받지 않는다
		mvc.perform(multipart("/admin/estimates/new/file").file(new MockMultipartFile("file", "a.txt", "text/plain", new byte[] { 1 })).session(admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("사진이나 PDF 파일만 첨부할 수 있어요."));

		mvc.perform(multipart("/admin/estimates/new/file").file(new MockMultipartFile("file", "수기견적서_김등록.jpg", "image/jpeg", JPG)).session(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.next").value("photo-preview"));
		mvc.perform(get("/admin/estimates/new").param("state", "photo-preview").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("수기견적서_김등록.jpg")));
		mvc.perform(get("/admin/estimates/new/file").session(admin)).andExpect(status().isOk()).andExpect(content().bytes(JPG));
		// 읽기 전에는 확인·수정 화면으로 가지 않는다
		mvc.perform(get("/admin/estimates/new").param("state", "review").session(admin))
				.andExpect(redirectedUrl("/admin/estimates/new?state=photo-preview"));

		// 읽기 (AI 연동 전이라 빈 칸) → 확인이 필요한 항목
		mvc.perform(post("/admin/estimates/new/read").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.next").value("unrecognized"));
		mvc.perform(get("/admin/estimates/new").param("state", "unrecognized").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("명품익스프레스")));
		mvc.perform(get("/admin/estimates/new").param("state", "register").session(admin))
				.andExpect(redirectedUrl("/admin/estimates/new?state=review"));

		// 확인·수정: 잘못된 값은 안내, 맞으면 보관
		mvc.perform(post("/admin/estimates/new/sheet").session(admin).contentType(MediaType.APPLICATION_JSON).content(sheet("")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("담당자를 입력해 주세요."));
		mvc.perform(post("/admin/estimates/new/sheet").session(admin).contentType(MediaType.APPLICATION_JSON).content(sheet("김등록")))
				.andExpect(status().isOk());
		mvc.perform(get("/admin/estimates/new").param("state", "review").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("광주 서구 상무중앙로 110")));
		mvc.perform(get("/admin/estimates/new").param("state", "register").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("견적일 26.09.04 · 1,420,000원")));

		// 등록: 회원 연결 + 견적 승인
		MvcResult res = mvc.perform(post("/admin/estimates/new").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memberId\":" + memberId + ",\"status\":\"02\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("김등록")).andReturn();
		Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id").toString());
		Map<String, Object> q = jdbc.queryForMap("SELECT * FROM TBL_QUOTE WHERE QUOTE_ID = ?", id);
		assertThat(q.get("SOURCE_TYPE")).isEqualTo("02");
		assertThat(q.get("STATUS")).isEqualTo("02");
		assertThat(((BigDecimal) q.get("MEMBER_ID")).longValue()).isEqualTo(memberId);
		assertThat(((BigDecimal) q.get("ADMIN_ID")).longValue()).isEqualTo(adminId);
		assertThat(q.get("APPROVED_AT")).isNotNull();
		assertThat(q.get("EXPIRES_AT")).isNull();
		assertThat(q.get("REQUEST_ID")).isNull();
		assertThat(q.get("CONTACT_NAME")).isEqualTo("김등록");
		assertThat(q.get("CONTACT_PHONE")).isEqualTo(memberPhone);
		assertThat(((BigDecimal) q.get("TOTAL_AMOUNT")).longValue()).isEqualTo(1_420_000);
		assertThat(((BigDecimal) q.get("DEPOSIT_AMOUNT")).longValue()).isEqualTo(142_000);
		assertThat(q.get("WORK_TYPE")).isEqualTo("04");
		assertThat(q.get("FROM_LADDER_USE")).isEqualTo("Y");
		assertThat((String) q.get("QUOTE_NO")).startsWith("QT-");
		assertThat(jdbc.queryForList("SELECT CATEGORY || ':' || ITEM_NAME || ':' || QUANTITY || UNIT FROM TBL_QUOTE_ITEM WHERE QUOTE_ID = ? ORDER BY SORT_ORDER",
				String.class, id)).containsExactly("02:모니터:6대", "02:TV:1대", "01:의자:6개");
		assertThat(jdbc.queryForList("SELECT OPTION_CODE FROM TBL_QUOTE_OPTION WHERE QUOTE_ID = ?", String.class, id)).containsExactly("04");
		Path document = Path.of((String) q.get("DOCUMENT_PATH"));
		assertThat(document.getFileName().toString()).isEqualTo("수기견적서_김등록.jpg");
		assertThat(Files.readAllBytes(document)).isEqualTo(JPG);
		// 등록이 끝나면 등록 중인 내용은 비운다
		mvc.perform(get("/admin/estimates/new").param("state", "register").session(admin)).andExpect(redirectedUrl("/admin/estimates/new"));

		// 견적 확인: 원본 파일 탭 · 내려받기
		mvc.perform(get("/admin/estimates/" + id).session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("수기견적서_김등록.jpg")))
				.andExpect(content().string(containsString("/admin/estimates/" + id + "/file?download=true")));
		mvc.perform(get("/admin/estimates/" + id + "/file").session(admin)).andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(JPG));
		mvc.perform(get("/admin/estimates/" + id + "/file").param("download", "true").session(admin)).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")));
		// 회원 견적 목록에도 보인다
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE WHERE MEMBER_ID = ?", Integer.class, memberId)).isEqualTo(1);

		// 등록 견적은 삭제할 수 있다 (품목·옵션 포함)
		assertThat((List<Object>) mvc.perform(get("/admin/estimates").session(admin)).andReturn().getModelAndView().getModel().get("rows"))
				.contains(id + "|견적 승인|김등록|26.09.04|Y");
		mvc.perform(post("/admin/estimates/" + id + "/delete").session(admin)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE WHERE QUOTE_ID = ?", Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_QUOTE_ITEM WHERE QUOTE_ID = ?", Integer.class, id)).isZero();
		mvc.perform(get("/admin/estimates/" + id + "/file").session(admin)).andExpect(status().isNotFound());
	}

	@Test
	void pdfRegisterWithoutMember() throws Exception {
		Long id = registerPdf("정등록", null, "01");
		Map<String, Object> q = jdbc.queryForMap("SELECT * FROM TBL_QUOTE WHERE QUOTE_ID = ?", id);
		assertThat(q.get("SOURCE_TYPE")).isEqualTo("03");
		assertThat(q.get("STATUS")).isEqualTo("01");
		assertThat(q.get("MEMBER_ID")).isNull();
		assertThat(q.get("ADMIN_ID")).isNull();
		assertThat(q.get("APPROVED_AT")).isNull();
		assertThat(q.get("CONTACT_NAME")).isEqualTo("정등록");
		assertThat(q.get("CONTACT_PHONE")).isEqualTo("010-1111-2222");
		assertThat(q.get("COMPANY_NAME")).isEqualTo("명품익스프레스");
		assertThat(((java.sql.Timestamp) q.get("ESTIMATE_DATE")).toLocalDateTime().toLocalDate()).isEqualTo(java.time.LocalDate.of(2023, 9, 14));
		assertThat(((BigDecimal) q.get("TOTAL_AMOUNT")).longValue()).isEqualTo(1_000_000);
		assertThat(((BigDecimal) q.get("DEPOSIT_AMOUNT")).longValue()).isEqualTo(100_000);
		assertThat(((BigDecimal) q.get("BALANCE_AMOUNT")).longValue()).isEqualTo(900_000);

		mvc.perform(get("/admin/estimates/" + id).param("tab", "file").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("견적서_정등록_230914.pdf")))
				.andExpect(content().string(containsString("PDF 등록")));
		mvc.perform(get("/admin/estimates/" + id + "/file").session(admin)).andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf")).andExpect(content().bytes(PDF));
	}

	@Test
	void pdfStepsAndStatusValidation() throws Exception {
		mvc.perform(multipart("/admin/estimates/new/file").file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF)).session(admin))
				.andExpect(jsonPath("$.next").value("pdf-preview"));
		mvc.perform(get("/admin/estimates/new").param("state", "pdf-preview").session(admin)).andExpect(status().isOk());
		// PDF 는 사진 단계로 가지 않는다
		mvc.perform(get("/admin/estimates/new").param("state", "review").session(admin))
				.andExpect(redirectedUrl("/admin/estimates/new?state=pdf-preview"));
		mvc.perform(post("/admin/estimates/new/read").session(admin)).andExpect(status().isConflict());
		mvc.perform(post("/admin/estimates/new/pdf").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"정등록\",\"phone\":\"\",\"estimateDate\":\"\",\"total\":\"\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("견적일을 입력해 주세요."));
		// 확인 전에는 등록할 수 없다
		mvc.perform(post("/admin/estimates/new").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"01\"}"))
				.andExpect(status().isConflict());
		mvc.perform(post("/admin/estimates/new/pdf").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"정등록\",\"phone\":\"\",\"estimateDate\":\"23.09.14\",\"total\":\"\"}"))
				.andExpect(status().isOk());
		mvc.perform(post("/admin/estimates/new").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"04\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/admin/estimates/new").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":-1,\"status\":\"03\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("연결할 회원을 찾을 수 없어요."));
		// 견적 확정으로 등록 (총 비용 없음)
		MvcResult res = mvc.perform(post("/admin/estimates/new").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"03\"}"))
				.andExpect(status().isOk()).andReturn();
		Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id").toString());
		Map<String, Object> q = jdbc.queryForMap("SELECT STATUS, TOTAL_AMOUNT, APPROVED_AT, CONTACT_PHONE FROM TBL_QUOTE WHERE QUOTE_ID = ?", id);
		assertThat(q.get("STATUS")).isEqualTo("03");
		assertThat(q.get("TOTAL_AMOUNT")).isNull();
		assertThat(q.get("APPROVED_AT")).isNotNull();
		assertThat(q.get("CONTACT_PHONE")).isNull();
	}

	/** PDF 첨부 → 정보 입력 → 등록. 등록한 견적 ID */
	private Long registerPdf(String name, Long member, String status) throws Exception {
		mvc.perform(multipart("/admin/estimates/new/file")
				.file(new MockMultipartFile("file", "견적서_" + name + "_230914.pdf", "application/pdf", PDF)).session(admin))
				.andExpect(status().isOk());
		mvc.perform(post("/admin/estimates/new/pdf").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"phone\":\"010-1111-2222\",\"estimateDate\":\"23.09.14\",\"total\":\"1,000,000\"}"))
				.andExpect(status().isOk());
		mvc.perform(get("/admin/estimates/new").param("state", "register").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("견적일 23.09.14 · 1,000,000원")));
		MvcResult res = mvc.perform(post("/admin/estimates/new").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"memberId\":" + member + ",\"status\":\"" + status + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return Long.valueOf(com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id").toString());
	}

	/** 등록 확인·수정 값 (estimate.js collectRegSheet 와 같은 형식) */
	private static String sheet(String customer) {
		return """
				{"companyName":"명품익스프레스","customer":"%s","phone":"","estimateDate":"26.09.04",
				 "origin":"광주 북구 용봉로 77, 3층","moveDate":"26.09.20","destination":"광주 서구 상무중앙로 110, 12층","transportDate":"",
				 "items":[["모니터","6대","의자","6개","","","",""],["TV","1대","","","","","",""]],
				 "options":[{"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""},
				            {"on1":true,"on2":true,"cost":"50,000"},{"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""},
				            {"on1":false,"on2":false,"cost":""},{"on1":false,"on2":false,"cost":""}],
				 "workMethod":"포장","transports":[{"from":true,"to":true},{"from":false,"to":false},{"from":false,"to":false}],
				 "volume":"3.5","note":"","movingCost":"1,100,000","fromLadderFloor":"3","departureLadder":"120,000",
				 "toLadderFloor":"12","destinationLadder":"150,000"}
				""".formatted(customer);
	}

	/* ---------- 휴대폰 번호 변경 ---------- */

	private String lastCode(String phone) {
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(smsSender, atLeastOnce()).send(eq(phone), message.capture());
		Matcher m = Pattern.compile("\\d{6}").matcher(message.getValue());
		assertThat(m.find()).isTrue();
		return m.group();
	}

	@Test
	void phoneChangeSendVerifyAndSession() throws Exception {
		MockHttpSession user = new MockHttpSession();
		user.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, "등록테스트", memberPhone));
		String otherPhone = randomPhone();
		insertMember("다른회원", otherPhone);
		String newPhone = randomPhone();

		mvc.perform(get("/more/profile").session(user)).andExpect(status().isOk())
				.andExpect(content().string(containsString("/more/profile/phone/send")));
		mvc.perform(post("/more/profile/phone/send").session(user).param("phone", "0101234")).andExpect(status().isBadRequest());
		mvc.perform(post("/more/profile/phone/send").session(user).param("phone", memberPhone))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("지금 쓰고 있는 번호예요."));
		mvc.perform(post("/more/profile/phone/send").session(user).param("phone", otherPhone.replace("-", "")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("이미 가입된 번호예요. 다른 번호를 입력해 주세요."));

		mvc.perform(post("/more/profile/phone/send").session(user).param("phone", newPhone))
				.andExpect(status().isOk()).andExpect(jsonPath("$.seconds").value(180)).andExpect(jsonPath("$.phone").value(newPhone));
		mvc.perform(post("/more/profile/phone").session(user).param("phone", newPhone).param("code", "000000x"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("인증번호가 맞지 않아요. 다시 확인해 주세요."));
		// 인증번호를 받은 번호와 다르면 만료로 본다
		mvc.perform(post("/more/profile/phone").session(user).param("phone", randomPhone()).param("code", lastCode(newPhone)))
				.andExpect(status().isGone());

		mvc.perform(post("/more/profile/phone/send").session(user).param("phone", newPhone)).andExpect(status().isOk());
		mvc.perform(post("/more/profile/phone").session(user).param("phone", newPhone.replace("-", "")).param("code", lastCode(newPhone)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(newPhone));
		assertThat(jdbc.queryForObject("SELECT PHONE FROM TBL_MEMBER WHERE MEMBER_ID = ?", String.class, memberId)).isEqualTo(newPhone);
		assertThat(((LoginMember) user.getAttribute(HomeController.LOGIN_KEY)).phone()).isEqualTo(newPhone);
		mvc.perform(get("/more/profile").session(user)).andExpect(content().string(containsString(newPhone)));
		// 같은 인증번호로 다시 바꿀 수 없다
		mvc.perform(post("/more/profile/phone").session(user).param("phone", newPhone).param("code", "123456"))
				.andExpect(status().isGone());

		// 그 사이 다른 회원이 쓰게 된 번호는 바꾸지 않는다 (UNIQUE)
		assertThatThrownBy(() -> memberService.changePhone(memberId, otherPhone)).isInstanceOf(IllegalStateException.class)
				.hasMessage("이미 가입된 번호예요. 다른 번호를 입력해 주세요.");
	}

	/* ---------- 미리보기 ---------- */

	@Test
	void previewsRenderWithoutModel() throws Exception {
		mvc.perform(get("/dev/admin/home")).andExpect(status().isOk()).andExpect(content().string(containsString("주간 예약 현황")));
		for (String s : new String[] { "", "photo-preview", "analyzing", "review", "unrecognized", "full-view", "pdf-preview", "pdf-review", "register" }) {
			mvc.perform(get("/dev/admin/estimate/register").param("state", s)).andExpect(status().isOk());
		}
		mvc.perform(get("/dev/admin/estimate/register").param("state", "register").param("source", "pdf"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("정도현")));
		mvc.perform(get("/dev/admin/estimate/register").param("state", "review"))
				.andExpect(content().string(containsString("광주 북구 용봉로 77, 3층")));
		mvc.perform(get("/dev/admin/estimate/edit").param("source", "photo").param("state", "file"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("수기견적서_홍길동.jpg")));
		mvc.perform(get("/dev/admin/estimate/edit").param("source", "pdf").param("state", "file")).andExpect(status().isOk());
		mvc.perform(get("/dev/user/more/profile").param("state", "verify")).andExpect(status().isOk())
				.andExpect(content().string(containsString("phoneError")));
	}
}
