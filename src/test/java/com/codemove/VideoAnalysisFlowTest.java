package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import com.codemove.admin.LoginAdmin;
import com.codemove.web.admin.AdminAuthController;

import tools.jackson.databind.json.JsonMapper;

/** 영상 분석 테스트 화면: 관리자 전용 · JSON 직접 입력 계산(톤 포함) · 정답 비교 · 실패 기록(키 없음) · 형식 검증 · 기록 조회/삭제 · CSV. 외부 API 호출 없음 */
@SpringBootTest(properties = { "codemove.video-analysis.standard-csv=src/test/resources/video-analysis/standard.csv",
		"codemove.video-analysis.alias-csv=src/test/resources/video-analysis/alias.csv",
		"codemove.video-analysis.truck-csv=src/test/resources/video-analysis/truck.csv",
		"codemove.video-analysis.testset-dir=src/test/resources/video-analysis/testset",
		"codemove.video-analysis.run-dir=build/test-video-analysis", "codemove.video-analysis.gemini.api-key=",
		"codemove.video-analysis.price.input-per-million=", "codemove.video-analysis.price.output-per-million=" })
@AutoConfigureMockMvc
class VideoAnalysisFlowTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	MockHttpSession admin;

	@BeforeEach
	void setUp() {
		FileSystemUtils.deleteRecursively(Path.of("build/test-video-analysis").toFile());
		admin = new MockHttpSession();
		admin.setAttribute(AdminAuthController.ADMIN_KEY, new LoginAdmin(0L, "video-test", "영상테스트", "02"));
		admin.setAttribute(AdminAuthController.PW_CHANGED_KEY, true);
	}

	@Test
	void adminOnly() throws Exception {
		mvc.perform(get("/admin/video-analysis")).andExpect(status().is3xxRedirection());
		mvc.perform(get("/admin/video-analysis").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("품목 2개 · 규격 2행 · 별칭 3개 · 트럭 4종")))
				.andExpect(content().string(Matchers.containsString("API 키 미설정")))
				.andExpect(content().string(Matchers.containsString("house-test")));
	}

	@Test
	void manualJsonIsMappedCalculatedAndScored() throws Exception {
		String raw = """
				{"video_ok":true,"rooms":["주방","팬트리"],"memo":"정수기 렌탈 확인",
				 "items":[{"item_name":"냉장고","size_code":"03","quantity":1,"detected_name":"4도어 냉장고","room":"주방","needs_review":false},
				 {"item_name":"기타","size_code":"02","quantity":10,"detected_name":"플라스틱 바구니","room":"팬트리","needs_review":false},
				 {"item_name":"기타","size_code":"","quantity":1,"detected_name":"피아노","room":"거실","needs_review":false}]}""";
		String body = mvc.perform(post("/admin/video-analysis/manual").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("raw", raw))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.mode").value("MANUAL"))
				.andExpect(jsonPath("$.ai.rooms[1]").value("팬트리"))
				.andExpect(jsonPath("$.ai.memo").value("정수기 렌탈 확인"))
				.andExpect(jsonPath("$.result.lines[0].item.type").value("EXACT"))
				.andExpect(jsonPath("$.result.lines[1].item.type").value("ALIAS"))
				.andExpect(jsonPath("$.result.lines[2].item.type").value("UNMAPPED"))
				// 1.490 + 0.077 × 10 = 2.260 ㎥ → 적재 4.52 → 1톤, 무게는 냉장고만 137.0
				.andExpect(jsonPath("$.result.totalCbm").value(2.26))
				.andExpect(jsonPath("$.result.totalKg").value(137.0))
				.andExpect(jsonPath("$.load.loadCbm").value(4.52))
				.andExpect(jsonPath("$.load.label").value("1톤"))
				.andReturn().getResponse().getContentAsString();
		String id = JSON.readTree(body).get("id").asString();

		// 정답 비교: 정답 냉장고 1 + 박스 12, AI 냉장고 1 + 박스 10 → 정밀도 1, 재현율 11/13
		mvc.perform(get("/admin/video-analysis/runs/" + id + "/score").param("set", "house-test").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.score.precision").value(1.0))
				.andExpect(jsonPath("$.score.recall").value(0.846))
				.andExpect(jsonPath("$.expectedLoad.label").value("1톤"))
				.andExpect(jsonPath("$.score.expectedOther").value(1))
				.andExpect(jsonPath("$.score.detectedOther").value(1));
		mvc.perform(get("/admin/video-analysis/runs/" + id + "/score").param("set", "../없음").session(admin))
				.andExpect(status().isNotFound());

		// 기록 조회 · CSV · 삭제
		mvc.perform(get("/admin/video-analysis/runs/" + id).session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCbm").value(2.26));
		mvc.perform(get("/admin/video-analysis/runs.csv").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString(id)));
		mvc.perform(post("/admin/video-analysis/runs/" + id + "/delete").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/video-analysis/runs/" + id).session(admin)).andExpect(status().isNotFound());
	}

	@Test
	void rejectedVideoIsRecordedWithCode() throws Exception {
		mvc.perform(post("/admin/video-analysis/manual").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("raw", "{\"video_ok\":false,\"reject_reason\":\"너무 어두움\",\"rooms\":[],\"items\":[]}"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("03"));
		mvc.perform(post("/admin/video-analysis/manual").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("raw", "깨진 응답"))))
				.andExpect(jsonPath("$.errorCode").value("07"));
	}

	@Test
	void uploadWithoutKeyIsRecordedAsFailure() throws Exception {
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "a.txt", "text/plain", new byte[] { 1 }))
				.session(admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("01"));
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "room.mp4", "video/mp4", new byte[] { 1, 2 }))
				.param("durationSec", "999").session(admin)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("02"))
				.andExpect(jsonPath("$.message").value("5초~5분 길이로 다시 찍어 주세요."));
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "room.mp4", "video/mp4", new byte[] { 1, 2 }))
				.param("durationSec", "2").session(admin)).andExpect(status().isBadRequest());
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "room.mp4", "video/mp4", new byte[] { 1, 2 }))
				.param("durationSec", "72.5").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("09"))
				.andExpect(jsonPath("$.video.durationSec").value(72.5));
		try (var files = Files.list(Path.of("build/test-video-analysis/videos"))) {
			assertThat(files.count()).isEqualTo(1);
		}
	}

	@Test
	void asyncUploadStatusAndCompare() throws Exception {
		// 비동기: 저장만 하고 202 + 실행 ID → 상태가 DONE 이 될 때까지 확인 (키가 없으니 곧 09 로 끝남)
		String body = mvc.perform(multipart("/admin/video-analysis/videos")
				.file(new MockMultipartFile("video", "house-test.mp4", "video/mp4", new byte[] { 1, 2 })).param("durationSec", "60")
				.param("async", "true").session(admin))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("RUNNING")).andReturn().getResponse()
				.getContentAsString();
		String id = JSON.readTree(body).get("id").asString();
		String state = "";
		for (int i = 0; i < 50 && !"DONE".equals(state); i++) {
			Thread.sleep(100);
			state = JSON.readTree(mvc.perform(get("/admin/video-analysis/runs/" + id + "/status").session(admin))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("state").asString();
		}
		assertThat(state).isEqualTo("DONE");
		mvc.perform(get("/admin/video-analysis/runs/" + id + "/status").session(admin))
				.andExpect(jsonPath("$.run.errorCode").value("09"));
		mvc.perform(get("/admin/video-analysis/runs/../x/status").session(admin)).andExpect(status().is4xxClientError());

		// 조건별 비교: 영상 이름 = 정답 목록 이름(house-test) 인 기록만, 실패는 이유별로
		mvc.perform(get("/admin/video-analysis/compare").param("set", "house-test").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[0].attempts").value(1))
				.andExpect(jsonPath("$.rows[0].successes").value(0))
				.andExpect(jsonPath("$.rows[0].errors['09']").value(1));
		mvc.perform(get("/admin/video-analysis/compare").param("set", "없음").session(admin)).andExpect(status().isNotFound());
	}

	@Test
	void manualRejectsEmpty() throws Exception {
		mvc.perform(post("/admin/video-analysis/manual").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"raw\":\" \"}"))
				.andExpect(status().isBadRequest());
	}
}
