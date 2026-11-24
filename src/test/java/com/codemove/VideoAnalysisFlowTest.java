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

/** 영상 분석 테스트 화면: 관리자 전용 · JSON 직접 입력 계산 · 실패 기록(키 없음) · 형식 검증 · 기록 조회/삭제 · CSV. 외부 API 호출 없음 */
@SpringBootTest(properties = { "codemove.video-analysis.standard-csv=src/test/resources/video-analysis/standard.csv",
		"codemove.video-analysis.run-dir=build/test-video-analysis", "codemove.video-analysis.gemini.api-key=",
		"codemove.video-analysis.price.input-per-million=", "codemove.video-analysis.price.output-per-million=" })
@AutoConfigureMockMvc
class VideoAnalysisFlowTest {

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
				.andExpect(content().string(org.hamcrest.Matchers.containsString("품목 2개 · 규격 2행")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("API 키 미설정")));
	}

	@Test
	void manualJsonIsMappedAndCalculated() throws Exception {
		String raw = """
				{"items":[{"detected_name":"양문형 냉장고","standard_name":"냉장고","category":"가전","quantity":1,"size_grade":"대형","needs_review":false},
				 {"detected_name":"플라스틱 바구니","standard_name":"","category":"생활용품","quantity":10,"size_grade":"중형","needs_review":false},
				 {"detected_name":"피아노","standard_name":"","category":"가구","quantity":1,"size_grade":"대형","needs_review":false}]}""";
		String body = mvc.perform(post("/admin/video-analysis/manual").session(admin).contentType(MediaType.APPLICATION_JSON)
				.content(new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.Map.of("raw", raw))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.mode").value("MANUAL"))
				.andExpect(jsonPath("$.result.lines[0].item.method").value("STANDARD"))
				.andExpect(jsonPath("$.result.lines[1].item.method").value("ALIAS"))
				.andExpect(jsonPath("$.result.lines[2].item.method").value("UNMAPPED"))
				// 1.490 + 0.077 × 10 = 2.260 ㎥, 무게는 냉장고만 137.0 (이사박스 무게 없음)
				.andExpect(jsonPath("$.result.totalCbm").value(2.26))
				.andExpect(jsonPath("$.result.totalKg").value(137.0))
				.andExpect(jsonPath("$.result.kgMissing").value(1))
				.andExpect(jsonPath("$.result.reviewCount").value(1))
				.andReturn().getResponse().getContentAsString();
		String id = new tools.jackson.databind.json.JsonMapper().readTree(body).get("id").asString();

		// 기록 조회 · 목록 · CSV · 삭제
		mvc.perform(get("/admin/video-analysis/runs/" + id).session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.result.totalCbm").value(2.26));
		mvc.perform(get("/admin/video-analysis/runs.csv").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(id)));
		mvc.perform(post("/admin/video-analysis/runs/" + id + "/delete").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/video-analysis/runs/" + id).session(admin)).andExpect(status().isNotFound());
	}

	@Test
	void uploadWithoutKeyIsRecordedAsFailure() throws Exception {
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "a.txt", "text/plain", new byte[] { 1 }))
				.session(admin)).andExpect(status().isBadRequest());
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "room.mp4", "video/mp4", new byte[] { 1, 2 }))
				.param("durationSec", "999").session(admin)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("영상은 120초까지 분석할 수 있어요."));
		mvc.perform(multipart("/admin/video-analysis/videos").file(new MockMultipartFile("video", "room.mp4", "video/mp4", new byte[] { 1, 2 }))
				.param("durationSec", "12.5").session(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("NO_KEY"))
				.andExpect(jsonPath("$.video.durationSec").value(12.5));
		try (var files = Files.list(Path.of("build/test-video-analysis/videos"))) {
			assertThat(files.count()).isEqualTo(1);
		}
	}

	@Test
	void manualRejectsEmpty() throws Exception {
		mvc.perform(post("/admin/video-analysis/manual").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"raw\":\" \"}"))
				.andExpect(status().isBadRequest());
	}
}
