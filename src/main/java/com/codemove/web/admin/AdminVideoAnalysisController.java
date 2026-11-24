package com.codemove.web.admin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.videoanalysis.AnalysisPrompt;
import com.codemove.videoanalysis.AnalysisRun;
import com.codemove.videoanalysis.AnalysisRunStore;
import com.codemove.videoanalysis.GroundTruthScorer;
import com.codemove.videoanalysis.ItemStandardCatalog;
import com.codemove.videoanalysis.VideoAnalysisService;

/**
 * 영상 분석 테스트 화면 (관리자 로그인 필요, 개발 · 검증용). 실제 견적 흐름에는 아직 연결하지 않는다.
 * 업로드 분석 · JSON 직접 입력 · 같은 영상 다시 분석 · 실행 기록 보기 · 삭제 · CSV 내보내기 · 기준표 다시 읽기
 */
@Controller
@RequestMapping("/admin/video-analysis")
public class AdminVideoAnalysisController {

	private static final int RECENT = 30;

	private final VideoAnalysisService service;
	private final AnalysisRunStore store;
	private final GroundTruthScorer scorer;

	public AdminVideoAnalysisController(VideoAnalysisService service, AnalysisRunStore store, GroundTruthScorer scorer) {
		this.service = service;
		this.store = store;
		this.scorer = scorer;
	}

	@GetMapping
	public String page(Model model) {
		model.addAttribute("catalog", service.catalog());
		model.addAttribute("geminiEnabled", service.geminiEnabled());
		model.addAttribute("model", service.model());
		model.addAttribute("promptVersion", AnalysisPrompt.VERSION);
		model.addAttribute("limits", service.properties());
		model.addAttribute("runs", store.recent(RECENT));
		model.addAttribute("testsets", scorer.sets());
		model.addAttribute("loadFactor", service.loadFactor());
		return "admin/video-analysis/index";
	}

	@PostMapping("/videos")
	@ResponseBody
	public ResponseEntity<Object> upload(@RequestParam("video") MultipartFile video,
			@RequestParam(required = false) Double durationSec, @RequestParam(required = false) Long uploadStartedAt) {
		// 업로드 시간: 브라우저가 보내기 시작한 시각 ~ 서버가 다 받은 시각 (같은 PC 시계 기준 근사값, 이상하면 기록하지 않음)
		long elapsed = uploadStartedAt == null ? -1 : System.currentTimeMillis() - uploadStartedAt;
		Long uploadMs = elapsed >= 0 && elapsed < 3_600_000 ? elapsed : null;
		try {
			return ResponseEntity.ok(service.analyzeUpload(video, durationSec, uploadMs));
		} catch (VideoAnalysisService.RejectedUpload e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "errorCode", e.code()));
		}
	}

	@PostMapping("/manual")
	@ResponseBody
	public ResponseEntity<Object> manual(@RequestBody Map<String, String> body) {
		String raw = body.getOrDefault("raw", "");
		if (raw.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("message", "JSON 을 입력해 주세요."));
		}
		return ResponseEntity.ok(service.analyzeManual(raw, body.get("baseRunId")));
	}

	@GetMapping("/runs/{id}")
	@ResponseBody
	public ResponseEntity<AnalysisRun> run(@PathVariable String id) {
		AnalysisRun run = store.find(id);
		return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(run);
	}

	/** 실행 결과를 정답 목록과 비교 (정밀도 · 재현율 · F1 · CBM 오차). 없는 기록 · 테스트셋이면 404 */
	@GetMapping("/runs/{id}/score")
	@ResponseBody
	public ResponseEntity<GroundTruthScorer.Score> score(@PathVariable String id, @RequestParam String set) {
		AnalysisRun run = store.find(id);
		if (run == null || run.result() == null) {
			return ResponseEntity.notFound().build();
		}
		GroundTruthScorer.Score s = scorer.score(set, run.result().lines().stream().map(l -> l.item()).toList(), service.catalog());
		return s == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(s);
	}

	@PostMapping("/runs/{id}/rerun")
	@ResponseBody
	public ResponseEntity<Object> rerun(@PathVariable String id) {
		AnalysisRun run = service.rerun(id);
		return run == null ? ResponseEntity.badRequest().body(Map.of("message", "다시 분석할 영상이 없어요."))
				: ResponseEntity.ok(run);
	}

	@PostMapping("/runs/{id}/delete")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
		return store.delete(id) ? ResponseEntity.ok(Map.of("ok", true)) : ResponseEntity.notFound().build();
	}

	@PostMapping("/catalog/reload")
	@ResponseBody
	public Map<String, Object> reload() {
		ItemStandardCatalog.Snapshot s = service.reloadCatalog();
		return Map.of("rows", s.list().size(), "items", s.names().size(), "aliases", s.aliases().size(), "trucks",
				s.trucks().size(), "error", s.error() == null ? "" : s.error());
	}

	@GetMapping(value = "/prompt", produces = "text/plain;charset=UTF-8")
	@ResponseBody
	public String prompt() {
		return service.prompt();
	}

	/** 실행 기록 CSV (엑셀용 BOM 포함): 최근 기록 전체 */
	@GetMapping("/runs.csv")
	public ResponseEntity<byte[]> export() {
		StringBuilder sb = new StringBuilder("﻿실행ID,기준실행,시각,방식,성공,오류코드,오류,모델,모델버전,생각수준,프롬프트,영상,크기MB,길이초,"
				+ "입력토큰,출력토큰,생각토큰,총토큰,비용,통화,단가기준일,업로드ms,저장ms,AI파일ms,APIms,매핑ms,전체ms,재시도,품목수,매핑수,확인필요,총CBM,총kg,무게없음,적재CBM,톤,차량\n");
		List<AnalysisRun> runs = store.recent(Integer.MAX_VALUE);
		for (AnalysisRun r : runs) {
			var u = r.usage();
			var c = r.cost();
			var t = r.timings();
			var v = r.video();
			var res = r.result();
			sb.append(String.join(",", List.of(r.id(), s(r.baseRunId()), s(r.createdAt()), r.mode(), String.valueOf(r.success()),
					s(r.errorCode()), csv(s(r.errorName())), s(r.model()), s(r.modelVersion()), s(r.thinkingLevel()), s(r.promptVersion()), csv(v == null ? "" : v.fileName()),
					v == null ? "" : String.format("%.2f", v.sizeBytes() / 1048576.0), v == null ? "" : s(v.durationSec()),
					u == null ? "" : s(u.promptTokens()), u == null ? "" : s(u.outputTokens()), u == null ? "" : s(u.thoughtsTokens()),
					u == null ? "" : s(u.totalTokens()), c == null ? "" : s(c.amount()), c == null ? "" : s(c.currency()),
					c == null ? "" : s(c.asOf()), s(t == null ? null : t.uploadMs()), s(t == null ? null : t.saveMs()), s(t == null ? null : t.aiFileMs()),
					s(t == null ? null : t.apiMs()), s(t == null ? null : t.mappingMs()), s(t == null ? null : t.totalMs()),
					s(r.retries()), s(r.itemCount()), s(r.mappedCount()), s(r.reviewCount()),
					res == null ? "" : s(res.totalCbm()), res == null ? "" : s(res.totalKg()), res == null ? "" : s(res.kgMissing()), r.load() == null ? "" : s(r.load().loadCbm()), r.load() == null ? "" : s(r.load().ton()), r.load() == null ? "" : csv(r.load().label()))))
					.append('\n');
		}
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"video-analysis-runs.csv\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String s(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String csv(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
