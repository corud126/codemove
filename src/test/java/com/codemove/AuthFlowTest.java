package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.code.CodeService;
import com.codemove.common.sms.SmsSender;
import com.codemove.member.LoginMember;
import com.codemove.member.SocialAccount;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.AuthController;
import com.codemove.web.user.HomeController;

/** 회원가입 · 휴대전화 로그인 · 관리자 로그인 흐름 (팀 DB 사용, 테스트마다 롤백) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CodeService codeService;

	@MockitoBean
	private SmsSender smsSender;

	/** 다른 데이터와 겹치지 않는 테스트 번호 (010-9XXX-XXXX) */
	private static String testPhone() {
		return String.format("010-9%03d-%04d", ThreadLocalRandom.current().nextInt(1000),
				ThreadLocalRandom.current().nextInt(10000));
	}

	/** 마지막으로 보낸 문자에서 인증번호 6자리를 꺼낸다 */
	private String lastCode(String phone) {
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(smsSender, atLeastOnce()).send(eq(phone), message.capture());
		Matcher m = Pattern.compile("\\d{6}").matcher(message.getValue());
		assertThat(m.find()).isTrue();
		return m.group();
	}

	@Test
	void commonCodes() {
		assertThat(codeService.list("LOGIN_TYPE")).hasSize(3);
		assertThat(codeService.name("LOGIN_TYPE", "01")).isEqualTo("카카오");
		assertThat(codeService.name("LOGIN_TYPE", "99")).isNull();
	}

	@Test
	void signupThenPhoneLogin() throws Exception {
		String phone = testPhone();
		MockHttpSession session = new MockHttpSession();

		mvc.perform(post("/signup").session(session).param("name", "테스트").param("phone", phone.replace("-", ""))
				.param("agreed", "true"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.redirect").value("/signup/verify"));
		mvc.perform(get("/signup/verify").session(session)).andExpect(status().isOk());

		mvc.perform(post("/signup/verify").session(session).param("code", "000000x"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/signup/verify").session(session).param("code", lastCode(phone)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.redirect").value("/signup/welcome"));

		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		assertThat(member.name()).isEqualTo("테스트");
		assertThat(member.phone()).isEqualTo(phone);
		assertThat(jdbc.queryForObject("SELECT IS_VERIFIED FROM TBL_MEMBER WHERE PHONE = ?", String.class, phone))
				.isEqualTo("Y");

		// 같은 번호로 다시 가입할 수 없다
		mvc.perform(post("/signup").param("name", "테스트").param("phone", phone).param("agreed", "true"))
				.andExpect(status().isConflict());

		// 휴대전화 로그인
		MockHttpSession login = new MockHttpSession();
		mvc.perform(post("/login/phone/send").session(login).param("phone", phone)).andExpect(status().isOk());
		mvc.perform(post("/login/phone").session(login).param("phone", phone).param("code", lastCode(phone)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.redirect").value("/"));
		assertThat(login.getAttribute(HomeController.LOGIN_KEY)).isNotNull();
		mvc.perform(get("/").session(login)).andExpect(status().isOk());
	}

	@Test
	void phoneLoginRejectsUnknownNumber() throws Exception {
		mvc.perform(post("/login/phone/send").param("phone", testPhone())).andExpect(status().isNotFound());
		verify(smsSender, never()).send(anyString(), anyString());
	}

	/** 이름·번호 입력 → 인증번호 확인까지. 인증 완료 응답의 redirect 를 돌려준다 */
	private String signup(MockHttpSession session, String name, String phone) throws Exception {
		mvc.perform(post("/signup").session(session).param("name", name).param("phone", phone).param("agreed", "true"))
				.andExpect(status().isOk());
		String body = mvc.perform(post("/signup/verify").session(session).param("code", lastCode(phone)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"redirect\":\"([^\"]+)\"").matcher(body);
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	@Test
	void withdrawErasesPersonalInfoAndAllowsNewSignup() throws Exception {
		String phone = testPhone();
		MockHttpSession session = new MockHttpSession();
		signup(session, "탈퇴자", phone);
		Long oldId = ((LoginMember) session.getAttribute(HomeController.LOGIN_KEY)).memberId();
		jdbc.update("INSERT INTO TBL_SOCIAL (SOCIAL_ID, MEMBER_ID, LOGIN_TYPE, SOCIAL_NO) VALUES (SEQ_SOCIAL.NEXTVAL, ?, '01', ?)",
				oldId, "k" + oldId);

		mvc.perform(get("/more/profile").session(session)).andExpect(status().isOk());
		mvc.perform(post("/more/withdraw").session(session)).andExpect(redirectedUrl("/onboarding"));

		var row = jdbc.queryForMap("SELECT MEMBER_NAME, PHONE, IS_ACTIVE, WITHDRAWN_AT FROM TBL_MEMBER WHERE MEMBER_ID = ?", oldId);
		assertThat(row.get("MEMBER_NAME")).isEqualTo("탈퇴회원");
		assertThat(row.get("PHONE")).isEqualTo("W" + oldId);
		assertThat(row.get("IS_ACTIVE")).isEqualTo("N");
		assertThat(row.get("WITHDRAWN_AT")).isNotNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_SOCIAL WHERE MEMBER_ID = ?", Integer.class, oldId)).isZero();

		// 같은 번호로 다시 가입하면 새 회원
		MockHttpSession again = new MockHttpSession();
		assertThat(signup(again, "새회원", phone)).isEqualTo("/signup/welcome");
		assertThat(((LoginMember) again.getAttribute(HomeController.LOGIN_KEY)).memberId()).isNotEqualTo(oldId);
	}

	@Test
	void socialSignupLinksNewOrExistingMember() throws Exception {
		// 처음 보는 소셜 계정 + 새 번호 → 가입하면서 연결
		String phone = testPhone();
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.PENDING_SOCIAL_KEY, new SocialAccount("02", "naver-" + phone));
		assertThat(signup(session, "네이버", phone)).isEqualTo("/signup/welcome");
		Long memberId = ((LoginMember) session.getAttribute(HomeController.LOGIN_KEY)).memberId();

		// 다른 소셜 계정 + 이미 가입된 번호 → 기존 회원에 연결 후 홈
		MockHttpSession kakao = new MockHttpSession();
		kakao.setAttribute(AuthController.PENDING_SOCIAL_KEY, new SocialAccount("01", "kakao-" + phone));
		assertThat(signup(kakao, "카카오", phone)).isEqualTo("/");
		assertThat(((LoginMember) kakao.getAttribute(HomeController.LOGIN_KEY)).memberId()).isEqualTo(memberId);
		assertThat(jdbc.queryForList("SELECT LOGIN_TYPE FROM TBL_SOCIAL WHERE MEMBER_ID = ? ORDER BY LOGIN_TYPE",
				String.class, memberId)).containsExactly("01", "02");

		mvc.perform(get("/more/profile").session(kakao)).andExpect(status().isOk());
	}

	@Test
	void unknownOAuthProviderReturnsToLogin() throws Exception {
		mvc.perform(get("/login/oauth/apple")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/login/oauth/kakao/callback").param("code", "x").param("state", "forged"))
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void adminLoginForcesPasswordChange() throws Exception {
		String loginId = "t" + ThreadLocalRandom.current().nextInt(1_000_000);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) "
				+ "VALUES (SEQ_ADMIN.NEXTVAL, ?, ?, '테스트', ?)",
				loginId, new BCryptPasswordEncoder().encode("init1234"), testPhone());
		MockHttpSession session = new MockHttpSession();

		mvc.perform(post("/admin/login").param("id", loginId).param("pw", "wrong"))
				.andExpect(redirectedUrl("/admin/login?state=error"));
		mvc.perform(post("/admin/login").session(session).param("id", loginId).param("pw", "init1234"))
				.andExpect(redirectedUrl("/admin/password"));
		mvc.perform(get("/admin").session(session)).andExpect(redirectedUrl("/admin/password"));

		mvc.perform(post("/admin/password").session(session).param("current", "wrong").param("password", "newpass123"))
				.andExpect(redirectedUrl("/admin/password?state=error"));
		mvc.perform(post("/admin/password").session(session).param("current", "init1234").param("password", "newpass123"))
				.andExpect(redirectedUrl("/admin"));
		assertThat(session.getAttribute(AdminAuthController.PW_CHANGED_KEY)).isEqualTo(true);

		MockHttpSession again = new MockHttpSession();
		mvc.perform(post("/admin/login").session(again).param("id", loginId).param("pw", "newpass123"))
				.andExpect(redirectedUrl("/admin"));
	}
}
