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

import com.codemove.videoanalysis.AnalysisOutcomePolicy;
import com.codemove.videoanalysis.AnalysisPrompt;
import com.codemove.videoanalysis.AnalysisRun;
import com.codemove.videoanalysis.AnalysisRunStore;
import com.codemove.videoanalysis.GroundTruthScorer;
import com.codemove.videoanalysis.ItemStandardCatalog;
import com.codemove.videoanalysis.TruckLoadService;
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
	private final TruckLoadService trucks;
	private final com.codemove.videoanalysis.AnalysisOutcomePolicy outcomePolicy;
	private final com.codemove.request.ItemCatalog itemCatalog;
	private final com.codemove.common.code.CodeService codeService;

	public AdminVideoAnalysisController(VideoAnalysisService service, AnalysisRunStore store, GroundTruthScorer scorer,
			TruckLoadService trucks, com.codemove.request.ItemCatalog itemCatalog, com.codemove.common.code.CodeService codeService,
			com.codemove.videoanalysis.AnalysisOutcomePolicy outcomePolicy) {
		this.service = service;
		this.store = store;
		this.scorer = scorer;
		this.trucks = trucks;
		this.outcomePolicy = outcomePolicy;
		this.itemCatalog = itemCatalog;
		this.codeService = codeService;
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
		model.addAttribute("promptVersions", AnalysisPrompt.VERSIONS);
		return "admin/video-analysis/index";
	}

	@PostMapping("/videos")
	@ResponseBody
	public ResponseEntity<Object> upload(@RequestParam("video") MultipartFile video,
			@RequestParam(required = false) Double durationSec, @RequestParam(required = false) Long uploadStartedAt,
			@RequestParam(required = false) String model, @RequestParam(required = false) Double fps,
			@RequestParam(required = false) String prompt, @RequestParam(required = false) Boolean noFallback) {
		// 업로드 시간: 브라우저가 보내기 시작한 시각 ~ 서버가 다 받은 시각 (같은 PC 시계 기준 근사값, 이상하면 기록하지 않음)
		long elapsed = uploadStartedAt == null ? -1 : System.currentTimeMillis() - uploadStartedAt;
		Long uploadMs = elapsed >= 0 && elapsed < 3_600_000 ? elapsed : null;
		try {
			return ResponseEntity.ok(service.analyzeUpload(video, durationSec, uploadMs, new VideoAnalysisService.RunOptions(model, fps, prompt, Boolean.TRUE.equals(noFallback))));
		} catch (VideoAnalysisService.RejectedUpload e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "errorCode", e.code(), "outcome",
					outcomePolicy.forUploadReject(e.code(), e.getMessage(), 1)));
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
	public ResponseEntity<Map<String, Object>> score(@PathVariable String id, @RequestParam String set) {
		AnalysisRun run = store.find(id);
		if (run == null || run.result() == null) {
			return ResponseEntity.notFound().build();
		}
		GroundTruthScorer.Score s = scorer.score(set, run.result().lines().stream().map(l -> l.item()).toList(), service.catalog());
		if (s == null) {
			return ResponseEntity.notFound().build();
		}
		// 톤 비교: 정답 목록 부피로 낸 톤(대표님 기준) vs AI 결과 톤
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("score", s);
		out.put("expectedLoad", trucks.estimate(s.expectedCbm(), service.catalog().trucks()));
		out.put("detectedLoad", run.load());
		return ResponseEntity.ok(out);
	}

	/**
	 * 분석 결과가 고객 확인 화면 · 관리자 요청 품목으로 넘어갈 값 (흐름에 연결하지 않고 값만 만든다).
	 * 품목명 · 크기 · 개수 · 확인 필요 + 기준표 부피. 미매핑 품목은 AI 가 본 이름 그대로 넘어가고, 고객 앱 품목 목록에 없으면 표시한다.
	 * (지금 앱 품목표의 부피는 화면 시안용 임시 값이라 쓰지 않는다)
	 */
	@GetMapping("/runs/{id}/customer-items")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> customerItems(@PathVariable String id) {
		AnalysisRun run = store.find(id);
		if (run == null || run.result() == null) {
			return ResponseEntity.notFound().build();
		}
		java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
		int i = 0;
		for (var line : run.result().lines()) {
			var m = line.item();
			String name = m.standardName().isEmpty() ? m.ai().detectedName() : m.standardName();
			name = name == null || name.isBlank() ? "기타" : name.length() > 50 ? name.substring(0, 50) : name;
			int count = m.ai().quantity();
			Map<String, Object> it = new java.util.LinkedHashMap<>();
			it.put("name", name);
			it.put("size", codeService.userName("ITEM_SIZE", m.sizeCode()));
			it.put("count", count);
			it.put("review", m.needsReview());
			it.put("aiIndex", i++);
			it.put("category", codeService.userName("ITEM_CATEGORY", itemCatalog.find(name).category()));
			it.put("standardCbm", line.totalCbm());
			it.put("inAppCatalog", itemCatalog.find(name).pickable());
			items.add(it);
		}
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("items", items);
		out.put("standardTotalCbm", run.result().totalCbm());
		return ResponseEntity.ok(out);
	}

	/** 고객 견적 화면(2단계 품목 확인)을 미리보기로 열고 이 실행 결과를 넣는다. 제출 · 저장은 되지 않는다 (today 가 없으면 화면이 미리보기 모드) */
	@GetMapping("/runs/{id}/customer")
	public String customerPreview(@PathVariable String id, Model model) {
		ResponseEntity<Map<String, Object>> items = customerItems(id);
		if (!items.getStatusCode().is2xxSuccessful() || items.getBody() == null) {
			return "redirect:/admin/video-analysis";
		}
		model.addAttribute("pickable", itemCatalog.pickable());
		model.addAttribute("catalog", itemCatalog.forScript());
		model.addAttribute("aiPreviewItems", items.getBody().get("items"));
		return "user/quote/index";
	}

	/**
	 * 이 실행 결과를 고객에게 어떻게 안내할지 (분석이 어려운 영상 처리 규칙 미리보기).
	 * 시도 횟수는 다시 분석 · 재계산으로 이어진 기록(baseRunId)을 따라가 센다. attempt 를 주면 그 값으로 (남은 횟수 시험용)
	 */
	@GetMapping("/runs/{id}/outcome")
	@ResponseBody
	public ResponseEntity<AnalysisOutcomePolicy.Outcome> outcome(@PathVariable String id,
			@RequestParam(required = false) Integer attempt) {
		AnalysisRun run = store.find(id);
		if (run == null) {
			return ResponseEntity.notFound().build();
		}
		int attempts = attempt != null && attempt > 0 ? attempt : attempts(run);
		return ResponseEntity.ok(outcomePolicy.evaluate(run, attempts));
	}

	/** 기준 실행을 따라가며 센 분석 시도 횟수 (자기 자신 포함, 순환 방지로 최대 20) */
	private int attempts(AnalysisRun run) {
		int n = 1;
		String base = run.baseRunId();
		while (base != null && n < 20) {
			AnalysisRun prev = store.find(base);
			if (prev == null) {
				break;
			}
			n++;
			base = prev.baseRunId();
		}
		return n;
	}

	@PostMapping("/runs/{id}/rerun")
	@ResponseBody
	public ResponseEntity<Object> rerun(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> b = body == null ? Map.of() : body;
		Object fps = b.get("fps");
		VideoAnalysisService.RunOptions options = new VideoAnalysisService.RunOptions(text(b.get("model")),
				fps instanceof Number n && n.doubleValue() > 0 ? n.doubleValue() : null, text(b.get("prompt")),
				Boolean.TRUE.equals(b.get("noFallback")));
		AnalysisRun run = service.rerun(id, options);
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

	private static String text(Object value) {
		return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
	}

	private static String s(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String csv(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
