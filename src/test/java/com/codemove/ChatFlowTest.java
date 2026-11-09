package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.codemove.chat.ChatSettingService;
import com.codemove.member.LoginMember;
import com.codemove.web.admin.AdminAuthController;
import com.codemove.web.user.HomeController;

/** 채팅 · 챗봇 흐름 (팀 DB 사용, 테스트마다 롤백 · 첨부는 build/test-uploads) */
@SpringBootTest(properties = "codemove.chat.upload-dir=build/test-uploads/chat")
@AutoConfigureMockMvc
@Transactional
class ChatFlowTest {

	/** 다른 데이터와 겹치지 않는 낱말 */
	private static final String KEYWORD = "큐브레몬젤리";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ChatSettingService settingService;

	private Long memberId;
	private String memberName;
	private Long adminId;
	private Long guideTopicId;
	private MockHttpSession member;
	private MockHttpSession admin;

	@BeforeEach
	void setUp() {
		memberId = newMember();
		memberName = "채팅" + memberId;
		member = new MockHttpSession();
		member.setAttribute(HomeController.LOGIN_KEY, new LoginMember(memberId, memberName, "010-0000-0000"));

		adminId = jdbc.queryForObject("SELECT SEQ_ADMIN.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_ADMIN (ADMIN_ID, LOGIN_ID, PASSWORD, ADMIN_NAME, PHONE) VALUES (?, ?, 'x', '김상담', '010-0000-0000')",
				adminId, "c" + adminId);
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(adminId, "c" + adminId, "김상담", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);

		// 안내형 주제 + 로어북 + 이어서 질문 버튼 + 퀵메뉴
		guideTopicId = jdbc.queryForObject("SELECT SEQ_CHATBOT_TOPIC.NEXTVAL FROM DUAL", Long.class);
		jdbc.update("INSERT INTO TBL_CHATBOT_TOPIC (TOPIC_ID, TITLE, INTRO, TOPIC_TYPE) VALUES (?, '테스트 포장 주제', '포장 안내 첫 말', '01')",
				guideTopicId);
		jdbc.update("INSERT INTO TBL_LOREBOOK (LOREBOOK_ID, TOPIC_ID, KEYWORDS, CONTENT, SORT_ORDER) VALUES (SEQ_LOREBOOK.NEXTVAL, ?, ?, '포장 로어북 답변', 1)",
				guideTopicId, "#" + KEYWORD + "#박스");
		jdbc.update("INSERT INTO TBL_CHATBOT_ACTION (ACTION_ID, TOPIC_ID, LABEL, ACTION_TYPE, ACTION_VALUE, SORT_ORDER) "
				+ "VALUES (SEQ_CHATBOT_ACTION.NEXTVAL, ?, '박스 문의', '02', '박스는 언제 오나요?', 1)", guideTopicId);
		jdbc.update("INSERT INTO TBL_CHATBOT_ACTION (ACTION_ID, TOPIC_ID, LABEL, ACTION_TYPE, ACTION_VALUE, SORT_ORDER) "
				+ "VALUES (SEQ_CHATBOT_ACTION.NEXTVAL, ?, '견적 신청', '01', '/quote', 2)", guideTopicId);

		// 챗봇 켜고 상담 운영 켬
		settingService.saveOperation(true, true, "00:00", "23:50", null, null, adminId);
	}

	private Long newMember() {
		Long id = jdbc.queryForObject("SELECT SEQ_MEMBER.NEXTVAL FROM DUAL", Long.class);
		String phone = String.format("010-8%03d-%04d", ThreadLocalRandom.current().nextInt(1000),
				ThreadLocalRandom.current().nextInt(10000));
		jdbc.update("INSERT INTO TBL_MEMBER (MEMBER_ID, MEMBER_NAME, PHONE, IS_VERIFIED, TERMS_AGREED_AT) VALUES (?, ?, ?, 'Y', SYSDATE)",
				id, "채팅" + id, phone);
		return id;
	}

	private String roomStatus(Long mid) {
		return jdbc.queryForObject("SELECT STATUS FROM TBL_CHAT_ROOM WHERE ROOM_ID = (SELECT MAX(ROOM_ID) FROM TBL_CHAT_ROOM WHERE MEMBER_ID = ?)",
				String.class, mid);
	}

	private long lastMessageId(Long mid) {
		return jdbc.queryForObject("SELECT NVL(MAX(MESSAGE_ID), 0) FROM TBL_CHAT_MESSAGE WHERE ROOM_ID IN "
				+ "(SELECT ROOM_ID FROM TBL_CHAT_ROOM WHERE MEMBER_ID = ?)", Long.class, mid);
	}

	@Test
	void botAnswersThenAgentChatUntilEnd() throws Exception {
		jdbc.update("INSERT INTO TBL_QUICK_MENU (MENU_ID, TOPIC_ID, MENU_NAME, SORT_ORDER) VALUES (SEQ_QUICK_MENU.NEXTVAL, ?, '포장 퀵메뉴', 99)",
				guideTopicId);
		mvc.perform(get("/chat").session(member)).andExpect(status().isOk())
				.andExpect(content().string(containsString("포장 퀵메뉴")))
				.andExpect(content().string(containsString("data-live=\"true\"")));
		mvc.perform(get("/chat/messages").session(member)).andExpect(status().isOk())
				.andExpect(jsonPath("$.messages.length()").value(0));

		// 직접 입력 → 로어북 키워드로 답변 + 주제 버튼
		mvc.perform(post("/chat/messages").session(member).param("text", "혹시 " + KEYWORD + " 되나요?"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].role").value("user"))
				.andExpect(jsonPath("$.messages[1].role").value("bot"))
				.andExpect(jsonPath("$.messages[1].text").value("포장 로어북 답변"))
				.andExpect(jsonPath("$.messages[1].actions[0].prompt").value("박스는 언제 오나요?"))
				.andExpect(jsonPath("$.messages[1].actions[1].href").value("/quote"));
		assertThat(roomStatus(memberId)).isEqualTo("01");

		// 퀵메뉴(주제) → 챗봇이 처음 하는 말
		mvc.perform(post("/chat/messages").session(member).param("text", "포장 퀵메뉴").param("topicId", guideTopicId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.messages[1].text").value("포장 안내 첫 말"));
		Long botTopic = jdbc.queryForObject("SELECT TOPIC_ID FROM TBL_CHAT_MESSAGE WHERE MESSAGE_ID = ?", Long.class,
				lastMessageId(memberId));
		assertThat(botTopic).isEqualTo(guideTopicId);

		// 못 찾는 질문 → 기본 안내 + 상담사 연결 버튼
		mvc.perform(post("/chat/messages").session(member).param("text", "zzqqxx"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.messages[1].actions[*].agent").value(hasItem(true)));
		mvc.perform(post("/chat/messages").session(member).param("text", " ")).andExpect(status().isBadRequest());

		// 상담사 연결 요청 → 대기
		mvc.perform(post("/chat/agent").session(member)).andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].text").value("상담사 연결 요청"));
		assertThat(roomStatus(memberId)).isEqualTo("02");
		// 대기 중 메시지에는 챗봇이 답하지 않음
		mvc.perform(post("/chat/messages").session(member).param("text", "언제 연결되나요?"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(1));

		// 관리자 상담함 · 검색 · 채팅방
		mvc.perform(get("/admin/chats").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("data-member=\"" + memberId + "\"")));
		mvc.perform(get("/admin/chats/rooms").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString(memberName)));
		mvc.perform(get("/admin/chats/search").session(admin).param("q", "언제 연결"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.memberIds").value(hasItem(memberId.intValue())));
		mvc.perform(get("/admin/chats/" + memberId).session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("상담 대기 시작")))
				.andExpect(content().string(containsString("포장 로어북 답변")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_CHAT_MESSAGE C JOIN TBL_CHAT_ROOM R ON R.ROOM_ID = C.ROOM_ID "
				+ "WHERE R.MEMBER_ID = ? AND C.SENDER_TYPE = '01' AND C.IS_READ = 'N'", Integer.class, memberId)).isZero();
		mvc.perform(get("/admin/chats/" + memberId + "/messages").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("data-messages")));

		// 관리자 답장 → 상담 중 · 담당자
		long before = lastMessageId(memberId);
		mvc.perform(post("/admin/chats/" + memberId + "/messages").session(admin).param("text", "안녕하세요 상담사입니다"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
		assertThat(roomStatus(memberId)).isEqualTo("03");
		assertThat(jdbc.queryForObject("SELECT ADMIN_ID FROM TBL_CHAT_ROOM WHERE MEMBER_ID = ?", Long.class, memberId))
				.isEqualTo(adminId);

		// 고객 폴링 → 답장 도착 · 읽음
		mvc.perform(get("/chat/messages").session(member).param("after", String.valueOf(before)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("03"))
				.andExpect(jsonPath("$.messages[0].text").value("안녕하세요 상담사입니다"));
		assertThat(jdbc.queryForObject("SELECT IS_READ FROM TBL_CHAT_MESSAGE WHERE MESSAGE_ID = ?", String.class,
				lastMessageId(memberId))).isEqualTo("Y");

		// 상담 종료 → 더 보낼 수 없고, 고객이 다시 쓰면 새 방
		mvc.perform(post("/admin/chats/" + memberId + "/end").session(admin)).andExpect(status().isOk());
		assertThat(roomStatus(memberId)).isEqualTo("04");
		mvc.perform(post("/admin/chats/" + memberId + "/messages").session(admin).param("text", "추가 안내"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("종료된 상담입니다."));
		mvc.perform(post("/chat/messages").session(member).param("text", "다시 문의해요")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_CHAT_ROOM WHERE MEMBER_ID = ?", Integer.class, memberId))
				.isEqualTo(2);
		assertThat(roomStatus(memberId)).isEqualTo("01");
	}

	@Test
	void botOffGoesStraightToWaiting() throws Exception {
		settingService.saveOperation(false, true, "09:00", "18:00", "조금만 기다려 주세요", "지금은 상담 시간이 아니에요", adminId);
		mvc.perform(get("/chat").session(member)).andExpect(status().isOk())
				.andExpect(content().string(containsString("평일 09:00–18:00")));
		mvc.perform(post("/chat/messages").session(member).param("text", "상담하고 싶어요"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2));
		assertThat(roomStatus(memberId)).isEqualTo("02");
		mvc.perform(post("/chat/agent").session(member)).andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[1].text").value(containsString("이미 상담사 연결을 요청했어요")));
	}

	@Test
	void fileAttachmentOnlyForOwner() throws Exception {
		MockMultipartFile png = new MockMultipartFile("file", "거실_사진.png", "image/png", new byte[] { 1, 2, 3 });
		mvc.perform(multipart("/chat/files").file(png).session(member).param("text", "이 가구도 옮길 수 있나요?"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].attachment.name").value("거실_사진.png"))
				.andExpect(jsonPath("$.messages[0].attachment.image").value(true));
		Long fileId = jdbc.queryForObject("SELECT MAX(C.MESSAGE_ID) FROM TBL_CHAT_MESSAGE C JOIN TBL_CHAT_ROOM R ON R.ROOM_ID = C.ROOM_ID "
				+ "WHERE R.MEMBER_ID = ? AND C.MESSAGE_TYPE = '02'", Long.class, memberId);
		mvc.perform(get("/chat/files/" + fileId).session(member)).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"));
		mvc.perform(get("/admin/chats/files/" + fileId).session(admin)).andExpect(status().isOk());

		MockHttpSession other = new MockHttpSession();
		Long otherId = newMember();
		other.setAttribute(HomeController.LOGIN_KEY, new LoginMember(otherId, "남", "010-0000-0000"));
		mvc.perform(get("/chat/files/" + fileId).session(other)).andExpect(status().isNotFound());

		MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1 });
		mvc.perform(multipart("/chat/files").file(exe).session(member)).andExpect(status().isBadRequest());
	}

	@Test
	void adminManagesSettingsRepliesTopicsAndQuickMenus() throws Exception {
		// 운영 설정
		mvc.perform(get("/admin/chats/settings").session(admin)).andExpect(status().isOk());
		mvc.perform(post("/admin/chats/settings").session(admin).param("bot", "true").param("consult", "false")
				.param("start", "10:00").param("end", "19:00").param("delay", "지연 안내").param("away", ""))
				.andExpect(status().isOk());
		assertThat(settingService.get().getConsultEnabled()).isEqualTo("N");
		assertThat(settingService.get().getAwayMessage()).isNull();
		mvc.perform(post("/admin/chats/settings").session(admin).param("start", "19:00").param("end", "10:00"))
				.andExpect(status().isBadRequest());

		// 자주 쓰는 답변
		mvc.perform(post("/admin/chats/replies").session(admin).param("title", "테스트 답변").param("content", "내용입니다"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.reply.title").value("테스트 답변"));
		Long replyId = jdbc.queryForObject("SELECT MAX(REPLY_ID) FROM TBL_SAVED_REPLY WHERE TITLE = '테스트 답변'", Long.class);
		mvc.perform(post("/admin/chats/replies").session(admin).param("id", replyId.toString())
				.param("title", "고친 답변").param("content", "고친 내용")).andExpect(status().isOk());
		mvc.perform(get("/admin/chats/replies").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("고친 답변")));
		mvc.perform(post("/admin/chats/replies").session(admin).param("title", "").param("content", "x"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/admin/chats/replies/" + replyId + "/delete").session(admin)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_SAVED_REPLY WHERE REPLY_ID = ?", Integer.class, replyId)).isZero();

		// 챗봇 답변: 화면 값(공통코드 이름 · 화면 이름)으로 추가
		String topicJson = """
				{"id":null,"title":"테스트 예약 주제","intro":"","type":"예약 조회","active":true,
				 "refs":[{"keywords":"#무시","content":"조회형은 로어북을 쓰지 않음"}],
				 "actions":[{"label":"예약 보기","type":"화면 이동","value":"예약 상세"},{"label":"상담원","type":"상담원 연결","value":""}]}
				""";
		mvc.perform(post("/admin/chats/answers").session(admin).contentType(MediaType.APPLICATION_JSON).content(topicJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.topic.type").value("예약 조회"))
				.andExpect(jsonPath("$.topic.refs.length()").value(0))
				.andExpect(jsonPath("$.topic.actions[0].value").value("예약 상세"));
		Long topicId = jdbc.queryForObject("SELECT MAX(TOPIC_ID) FROM TBL_CHATBOT_TOPIC WHERE TITLE = '테스트 예약 주제'", Long.class);
		assertThat(jdbc.queryForObject("SELECT TOPIC_TYPE FROM TBL_CHATBOT_TOPIC WHERE TOPIC_ID = ?", String.class, topicId)).isEqualTo("02");
		assertThat(jdbc.queryForList("SELECT ACTION_VALUE FROM TBL_CHATBOT_ACTION WHERE TOPIC_ID = ? ORDER BY SORT_ORDER", String.class, topicId))
				.containsExactly("/move?tab=booking", null);
		mvc.perform(get("/admin/chats/answers").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("테스트 예약 주제")));

		// 예약 조회 주제로 물으면 예약이 없다는 안내
		mvc.perform(post("/chat/messages").session(member).param("text", "내 예약").param("topicId", topicId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.messages[1].text").value(containsString("예약이 없어요")));

		// 사용 안 함 → 삭제 (지난 답변의 주제 연결은 끊김)
		mvc.perform(post("/admin/chats/answers/" + topicId + "/active").session(admin).param("active", "false"))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT IS_ACTIVE FROM TBL_CHATBOT_TOPIC WHERE TOPIC_ID = ?", String.class, topicId)).isEqualTo("N");
		mvc.perform(post("/admin/chats/answers/" + topicId + "/delete").session(admin)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TBL_CHATBOT_TOPIC WHERE TOPIC_ID = ?", Integer.class, topicId)).isZero();

		// 채팅 시작 화면: 인사말 + 퀵메뉴 (기존 퀵메뉴는 지우고 새로 저장)
		String entryJson = "{\"greeting\":\"테스트 인사말\",\"menus\":[{\"id\":null,\"topicId\":" + guideTopicId
				+ ",\"label\":\"포장 버튼\",\"active\":true}]}";
		mvc.perform(post("/admin/chats/entry").session(admin).contentType(MediaType.APPLICATION_JSON).content(entryJson))
				.andExpect(status().isOk()).andExpect(jsonPath("$.menus.length()").value(1))
				.andExpect(jsonPath("$.menus[0].label").value("포장 버튼"));
		assertThat(settingService.get().getGreeting()).isEqualTo("테스트 인사말");
		mvc.perform(get("/admin/chats/entry").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/chat").session(member)).andExpect(content().string(containsString("포장 버튼")))
				.andExpect(content().string(containsString("테스트 인사말")));
		String dupJson = "{\"greeting\":\"인사\",\"menus\":[{\"topicId\":" + guideTopicId + ",\"label\":\"a\",\"active\":true},"
				+ "{\"topicId\":" + guideTopicId + ",\"label\":\"b\",\"active\":true}]}";
		mvc.perform(post("/admin/chats/entry").session(admin).contentType(MediaType.APPLICATION_JSON).content(dupJson))
				.andExpect(status().isBadRequest());
	}

	/** 컨트롤러 없이 여는 미리 보기(/dev/...)도 더미로 렌더링된다 */
	@Test
	void previewTemplatesRenderWithoutModel() throws Exception {
		for (String page : new String[] { "user/chat/index", "admin/chat/list", "admin/chat/detail", "admin/chat/settings",
				"admin/chat/entry", "admin/chat/answers", "admin/chat/replies" }) {
			mvc.perform(get("/dev/" + page).param("state", "info")).andExpect(status().isOk());
		}
		mvc.perform(get("/dev/user/chat/index").param("state", "reservation")).andExpect(status().isOk())
				.andExpect(content().string(containsString("예약 정보 보기")));
	}

	@Test
	void requiresLogin() throws Exception {
		mvc.perform(get("/admin/chats")).andExpect(redirectedUrl("/admin/login"));
		mvc.perform(get("/chat/messages")).andExpect(status().isUnauthorized());
		mvc.perform(get("/admin/chats/999999999").session(admin)).andExpect(redirectedUrl("/admin/chats"));
	}
}
