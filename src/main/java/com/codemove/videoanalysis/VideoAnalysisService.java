package com.codemove.videoanalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.videoanalysis.GeminiVideoClient.GeminiException;
import com.codemove.videoanalysis.GeminiVideoClient.Usage;

import lombok.extern.slf4j.Slf4j;

/**
 * 영상 분석 처리 순서: 영상 검증 · 저장 → Gemini 분석 → JSON 검증 → 표준 품목 매핑 → 부피 · 무게 → 톤 → 기록.
 * 테스트 화면과 나중의 실제 견적 흐름이 함께 쓸 수 있도록 화면 코드와 분리한다.
 * 실패(키 없음 · API 오류 · JSON 오류 · 판단 불가 영상)도 기록으로 남긴다 (재시도 · 실패 비용 측정). 실패 코드는 ANALYSIS_ERROR.
 */
@Slf4j
@Service
@EnableConfigurationProperties(VideoAnalysisProperties.class)
public class VideoAnalysisService {

	private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
	private static final Map<String, String> MIME = Map.of("mp4", "video/mp4", "mov", "video/mov", "webm", "video/webm",
			"3gp", "video/3gpp", "avi", "video/avi", "mpeg", "video/mpeg", "mpg", "video/mpg", "wmv", "video/wmv");
	/** GeminiException 코드 → ANALYSIS_ERROR */
	private static final Map<String, String> ERROR_CODE = Map.of("NO_KEY", "09", "HTTP_429", "06", "HTTP_5xx", "10",
			"NETWORK", "05");
	private static final AtomicLong LAST_ID = new AtomicLong();
	/** 분석 작업 (동시에 2개까지, 나머지는 줄을 섬) · 분석 중인 실행 ID → 시작 시각 */
	private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
	private final java.util.Map<String, Long> running = new java.util.concurrent.ConcurrentHashMap<>();

	private final VideoAnalysisProperties properties;
	private final ItemStandardCatalog catalog;
	private final VolumeWeightService volume;
	private final TruckLoadService trucks;
	private final GeminiVideoClient gemini;
	private final AnalysisRunStore store;

	public VideoAnalysisService(VideoAnalysisProperties properties, ItemStandardCatalog catalog, VolumeWeightService volume,
			TruckLoadService trucks, GeminiVideoClient gemini, AnalysisRunStore store) {
		this.properties = properties;
		this.catalog = catalog;
		this.volume = volume;
		this.trucks = trucks;
		this.gemini = gemini;
		this.store = store;
	}

	/** 업로드 검증 실패 (형식 01 · 용량/길이 02). AI 를 부르지 않으므로 기록하지 않고 화면에 바로 알린다 */
	public static class RejectedUpload extends IllegalArgumentException {

		private final String code;

		RejectedUpload(String code, String message) {
			super(message);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	/** 실행마다 고르는 실험 조건 (비우면 설정값): 주 모델 · 영상 초당 프레임 · 프롬프트 버전 · 예비 모델 쓰지 않기(그 모델 결과만 비교할 때) */
	public record RunOptions(String model, Double fps, String promptVersion, boolean noFallback) {

		public static final RunOptions DEFAULT = new RunOptions(null, null, null, false);

		public String prompt() {
			return promptVersion != null && AnalysisPrompt.VERSIONS.contains(promptVersion) ? promptVersion : AnalysisPrompt.VERSION;
		}
	}

	public AnalysisRun analyzeUpload(MultipartFile file, Double durationSec, Long uploadMs) {
		return analyzeUpload(file, durationSec, uploadMs, RunOptions.DEFAULT);
	}

	/** 업로드한 영상을 분석한다 (끝날 때까지 기다림). 형식 · 크기 · 길이가 맞지 않으면 RejectedUpload(화면 문구) */
	public AnalysisRun analyzeUpload(MultipartFile file, Double durationSec, Long uploadMs, RunOptions options) {
		Prepared p = prepareUpload(file, durationSec);
		return runGemini(p.id(), null, p.video(), uploadMs, p.saveMs(), p.started(), options);
	}

	/**
	 * 비동기: 영상 검증 · 저장만 하고 실행 ID 를 바로 돌려준다. 분석은 뒤에서 돌고, 상태는 status(id) 로 본다.
	 * (분석이 1~2분 걸려 요청을 붙잡고 있으면 고객 화면이 멈춘 것처럼 보이므로)
	 */
	public String startUpload(MultipartFile file, Double durationSec, Long uploadMs, RunOptions options) {
		Prepared p = prepareUpload(file, durationSec);
		submit(p.id(), () -> runGemini(p.id(), null, p.video(), uploadMs, p.saveMs(), p.started(), options));
		return p.id();
	}

	/** 비동기 다시 분석. 원래 실행 · 영상이 없으면 null */
	public String startRerun(String baseId, RunOptions options) {
		Prepared p = prepareRerun(baseId);
		if (p == null) {
			return null;
		}
		submit(p.id(), () -> runGemini(p.id(), baseId, p.video(), null, p.saveMs(), p.started(), options));
		return p.id();
	}

	/** RUNNING: 분석 중(elapsedSec) · DONE: 끝남(run) · 없으면 null */
	public record Status(String state, Long elapsedSec, AnalysisRun run) {
	}

	public Status status(String id) {
		AnalysisRun run = store.find(id);
		if (run != null) {
			return new Status("DONE", null, run);
		}
		Long since = running.get(id);
		return since == null ? null : new Status("RUNNING", (System.currentTimeMillis() - since) / 1000, null);
	}

	private void submit(String id, Runnable job) {
		running.put(id, System.currentTimeMillis());
		executor.submit(() -> {
			try {
				job.run();
			} catch (RuntimeException e) {
				log.error("영상 분석 작업 실패 {}", id, e);
			} finally {
				running.remove(id);
			}
		});
	}

	@jakarta.annotation.PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	private record Prepared(String id, AnalysisRun.Video video, long saveMs, long started) {
	}

	private Prepared prepareUpload(MultipartFile file, Double durationSec) {
		long started = System.currentTimeMillis();
		if (file == null || file.isEmpty()) {
			throw new RejectedUpload("01", "영상을 선택해 주세요.");
		}
		String name = file.getOriginalFilename() == null ? "video" : Path.of(file.getOriginalFilename()).getFileName().toString();
		String ext = extension(name);
		if (!properties.allowedExtensions().contains(ext) || !MIME.containsKey(ext)) {
			throw new RejectedUpload("01", "동영상 파일만 올릴 수 있어요 (" + String.join(" · ", properties.allowedExtensions()) + ").");
		}
		if (file.getSize() > properties.maxVideoMb() * 1024L * 1024L) {
			throw new RejectedUpload("02", "영상은 " + properties.maxVideoMb() + "MB 까지 올릴 수 있어요.");
		}
		if (durationSec != null && (durationSec > properties.maxDurationSec() || durationSec < properties.minDurationSec())) {
			throw new RejectedUpload("02", properties.minDurationSec() + "초~" + properties.maxDurationSec() / 60
					+ "분 길이로 다시 찍어 주세요.");
		}
		String id = newId();
		String stored = id + "." + ext;
		try {
			file.transferTo(store.videoDir().resolve(stored).toAbsolutePath());
		} catch (IOException e) {
			throw new IllegalStateException("영상을 저장하지 못했어요.", e);
		}
		long saveMs = System.currentTimeMillis() - started;
		AnalysisRun.Video video = new AnalysisRun.Video(name, file.getSize(), durationSec, MIME.get(ext), stored, null);
		return new Prepared(id, video, saveMs, started);
	}

	/** 저장된 영상으로 다시 분석 (같은 영상 반복 테스트). 영상은 새 실행 ID 로 복사한다. 원래 실행이 없으면 null */
	public AnalysisRun rerun(String baseId) {
		return rerun(baseId, RunOptions.DEFAULT);
	}

	/** 같은 영상을 조건(모델 · 프레임 · 프롬프트)을 바꿔 다시 분석 (끝날 때까지 기다림) */
	public AnalysisRun rerun(String baseId, RunOptions options) {
		Prepared p = prepareRerun(baseId);
		return p == null ? null : runGemini(p.id(), baseId, p.video(), null, p.saveMs(), p.started(), options);
	}

	/** 원래 실행의 영상을 새 실행 ID 로 복사. 없으면 null */
	private Prepared prepareRerun(String baseId) {
		long started = System.currentTimeMillis();
		AnalysisRun base = store.find(baseId);
		if (base == null || base.video() == null || base.video().storedName() == null) {
			return null;
		}
		Path source = store.videoPath(base.video().storedName());
		if (!Files.isRegularFile(source)) {
			return null;
		}
		String id = newId();
		String stored = id + "." + extension(base.video().storedName());
		try {
			Files.copy(source, store.videoDir().resolve(stored));
		} catch (IOException e) {
			throw new IllegalStateException("영상을 복사하지 못했어요.", e);
		}
		AnalysisRun.Video v = base.video();
		AnalysisRun.Video video = new AnalysisRun.Video(v.fileName(), v.sizeBytes(), v.durationSec(), v.mimeType(), stored, null);
		return new Prepared(id, video, System.currentTimeMillis() - started, started);
	}

	/** AI 응답 JSON 을 직접 넣어 매핑 · 계산만 (키 없이 시험, 결과를 고친 뒤 재계산) */
	public AnalysisRun analyzeManual(String raw, String baseRunId) {
		long started = System.currentTimeMillis();
		String base = AnalysisRunStore.safe(baseRunId) ? baseRunId : null;
		AnalysisRun run = finish(newId(), base, "MANUAL", null, "", "", "", "-", null, null, 0, raw,
				new AnalysisRun.Timings(null, null, null, null, null, null), started, null, null);
		store.save(run);
		return run;
	}

	public ItemStandardCatalog.Snapshot catalog() {
		return catalog.snapshot();
	}

	public ItemStandardCatalog.Snapshot reloadCatalog() {
		return catalog.reload();
	}

	public boolean geminiEnabled() {
		return gemini.enabled();
	}

	public String model() {
		return gemini.model();
	}

	public VideoAnalysisProperties properties() {
		return properties;
	}

	public BigDecimal loadFactor() {
		return trucks.loadFactor();
	}

	public String prompt() {
		return AnalysisPrompt.build(catalog.snapshot().list());
	}

	private AnalysisRun runGemini(String id, String baseId, AnalysisRun.Video video, Long uploadMs, long saveMs, long started,
			RunOptions options) {
		RunOptions opt = options == null ? RunOptions.DEFAULT : options;
		AnalysisRun run;
		String thinking = properties.gemini().thinkingLevel();
		try {
			GeminiVideoClient.Response res = gemini.analyze(store.videoPath(video.storedName()), video.mimeType(),
					AnalysisPrompt.build(opt.prompt(), catalog.snapshot().list()), opt.model(), opt.fps(), !opt.noFallback());
			AnalysisRun.Video sent = new AnalysisRun.Video(video.fileName(), video.sizeBytes(), video.durationSec(),
					video.mimeType(), video.storedName(), res.transfer());
			run = finish(id, baseId, "GEMINI", sent, res.model(), res.modelVersion(), thinking, opt.prompt(), opt.fps(), res.usage(), res.retries(),
					res.text(), new AnalysisRun.Timings(uploadMs, saveMs, res.fileUploadMs(), res.apiMs(), null, null), started,
					null, null);
			// 주 모델이 혼잡해 예비 모델로 넘어간 결과는 정확도가 크게 떨어진다 (house-01: 재현율 0.85 → 0.40) → 경고를 맨 앞에
			String primary = opt.model() == null || opt.model().isBlank() ? gemini.model() : opt.model().trim();
			if (!primary.equals(res.model())) {
				run = withWarning(run, "주 모델(" + primary + ")이 혼잡해 예비 모델(" + res.model()
						+ ")로 분석했어요. 개수를 적게 세는 경향이 있어 다시 분석을 권장해요.");
			}
		} catch (GeminiException e) {
			log.warn("영상 분석 실패 {}: {}", id, e.getMessage());
			run = finish(id, baseId, "GEMINI", video, opt.model() == null || opt.model().isBlank() ? gemini.model() : opt.model(), "", thinking,
					opt.prompt(), opt.fps(), null, e.retries(), null,
					new AnalysisRun.Timings(uploadMs, saveMs, null, null, null, null), started,
					ERROR_CODE.getOrDefault(e.code(), "08"), e.getMessage());
		}
		store.save(run);
		return run;
	}

	/** 응답 글을 검증 · 매핑 · 계산해서 기록을 만든다. errorCode 가 있으면 계산 없이 실패 기록 */
	private AnalysisRun finish(String id, String baseId, String mode, AnalysisRun.Video video, String model, String modelVersion,
			String thinking, String promptVersion, Double fps, Usage usage, int retries, String raw, AnalysisRun.Timings t, long started,
			String errorCode,
			String errorMessage) {
		ItemStandardCatalog.Snapshot table = catalog.snapshot();
		AnalysisRun.Catalog used = new AnalysisRun.Catalog(table.source(), table.loadedAt(), table.list().size(),
				table.aliases().size(), trucks.loadFactor());
		AnalysisRun.Cost cost = cost(usage, model, modelVersion);
		if (errorCode != null) {
			return new AnalysisRun(id, baseId, LocalDateTime.now(), mode, false, errorCode, errorMessage, video, model,
					modelVersion, thinking, fps, promptVersion, usage, cost, total(t, null, started), retries, raw, null,
					List.of(), List.of(), null, null, used);
		}
		long mapStart = System.currentTimeMillis();
		AiResultParser.Parsed parsed = AiResultParser.parse(raw);
		VolumeWeightService.Result result = volume.calculate(ItemMappingService.map(parsed.items(), table));
		TruckLoadService.Load load = trucks.estimate(result.totalCbm(), table.trucks());
		long mappingMs = System.currentTimeMillis() - mapStart;
		AnalysisRun.Ai ai = new AnalysisRun.Ai(parsed.videoOk(), parsed.rejectReason(), parsed.rooms(), parsed.memo(), parsed.state());
		String code = null;
		String message = null;
		if (parsed.broken()) {
			code = "07";
			message = "AI 응답을 해석하지 못했어요.";
		} else if (!parsed.videoOk()) {
			boolean quality = !parsed.state().qualityIssues().isEmpty() || parsed.rejectReason().matches(".*(어두|어둡|흔들|흐림|흐릿|화질|빛|밝기).*");
			code = quality ? "03" : "04";
			message = "AI 가 판단할 수 없는 영상이라고 답했어요: " + parsed.rejectReason();
		} else if (parsed.items().isEmpty()) {
			code = "04";
			message = "찾은 품목이 없어요.";
		}
		return new AnalysisRun(id, baseId, LocalDateTime.now(), mode, code == null, code, message, video, model, modelVersion,
				thinking, fps, promptVersion, usage, cost, total(t, mappingMs, started), retries, raw, ai, parsed.errors(),
				parsed.warnings(), result, load, used);
	}

	private static AnalysisRun withWarning(AnalysisRun r, String warning) {
		List<String> warnings = new java.util.ArrayList<>(List.of(warning));
		warnings.addAll(r.parseWarnings());
		return new AnalysisRun(r.id(), r.baseRunId(), r.createdAt(), r.mode(), r.success(), r.errorCode(), r.errorMessage(),
				r.video(), r.model(), r.modelVersion(), r.thinkingLevel(), r.fps(), r.promptVersion(), r.usage(), r.cost(),
				r.timings(), r.retries(), r.rawResponse(), r.ai(), r.parseErrors(), List.copyOf(warnings), r.result(), r.load(),
				r.catalog());
	}

	private static AnalysisRun.Timings total(AnalysisRun.Timings t, Long mappingMs, long started) {
		return new AnalysisRun.Timings(t.uploadMs(), t.saveMs(), t.aiFileMs(), t.apiMs(), mappingMs,
				System.currentTimeMillis() - started);
	}

	/**
	 * 입력 토큰 × 입력 단가 + (출력 + 생각) 토큰 × 출력 단가. 단가는 실제로 응답한 모델 버전 → 요청 모델 순서로 찾는다
	 * (예비 gemini-3.5-flash 는 응답 버전 3.6-flash 의 단가). 단가가 없으면 amount null
	 */
	AnalysisRun.Cost cost(Usage usage, String model, String modelVersion) {
		VideoAnalysisProperties.Price p = properties.price();
		if (usage == null) {
			return null;
		}
		VideoAnalysisProperties.ModelPrice m = p.find(model, modelVersion);
		if (m == null) {
			return new AnalysisRun.Cost(null, null, p.currency(), p.asOf(), null);
		}
		BigDecimal amount = BigDecimal.valueOf(usage.promptTokens()).multiply(m.inputPerMillion())
				.add(BigDecimal.valueOf((long) usage.outputTokens() + usage.thoughtsTokens()).multiply(m.outputPerMillion()))
				.divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
		return new AnalysisRun.Cost(m.inputPerMillion(), m.outputPerMillion(), p.currency(), p.asOf(), amount);
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** 시각 기반 ID (같은 밀리초에 두 번 만들면 1ms 뒤로) */
	private static String newId() {
		long now = System.currentTimeMillis();
		long id = LAST_ID.updateAndGet(last -> Math.max(last + 1, now));
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(id), ZoneId.systemDefault()).format(ID);
	}
}
