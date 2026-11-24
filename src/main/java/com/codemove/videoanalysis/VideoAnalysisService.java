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

	/** 업로드한 영상을 분석한다. 형식 · 크기 · 길이가 맞지 않으면 RejectedUpload(화면 문구) */
	public AnalysisRun analyzeUpload(MultipartFile file, Double durationSec, Long uploadMs) {
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
		return runGemini(id, null, video, uploadMs, saveMs, started);
	}

	/** 저장된 영상으로 다시 분석 (같은 영상 반복 테스트). 영상은 새 실행 ID 로 복사한다. 원래 실행이 없으면 null */
	public AnalysisRun rerun(String baseId) {
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
		return runGemini(id, baseId, video, null, System.currentTimeMillis() - started, started);
	}

	/** AI 응답 JSON 을 직접 넣어 매핑 · 계산만 (키 없이 시험, 결과를 고친 뒤 재계산) */
	public AnalysisRun analyzeManual(String raw, String baseRunId) {
		long started = System.currentTimeMillis();
		String base = AnalysisRunStore.safe(baseRunId) ? baseRunId : null;
		AnalysisRun run = finish(newId(), base, "MANUAL", null, "", "", "", null, 0, raw,
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

	private AnalysisRun runGemini(String id, String baseId, AnalysisRun.Video video, Long uploadMs, long saveMs, long started) {
		AnalysisRun run;
		String thinking = properties.gemini().thinkingLevel();
		try {
			GeminiVideoClient.Response res = gemini.analyze(store.videoPath(video.storedName()), video.mimeType(), prompt());
			AnalysisRun.Video sent = new AnalysisRun.Video(video.fileName(), video.sizeBytes(), video.durationSec(),
					video.mimeType(), video.storedName(), res.transfer());
			run = finish(id, baseId, "GEMINI", sent, res.model(), res.modelVersion(), thinking, res.usage(), res.retries(),
					res.text(), new AnalysisRun.Timings(uploadMs, saveMs, res.fileUploadMs(), res.apiMs(), null, null), started,
					null, null);
		} catch (GeminiException e) {
			log.warn("영상 분석 실패 {}: {}", id, e.getMessage());
			run = finish(id, baseId, "GEMINI", video, gemini.model(), "", thinking, null, e.retries(), null,
					new AnalysisRun.Timings(uploadMs, saveMs, null, null, null, null), started,
					ERROR_CODE.getOrDefault(e.code(), "08"), e.getMessage());
		}
		store.save(run);
		return run;
	}

	/** 응답 글을 검증 · 매핑 · 계산해서 기록을 만든다. errorCode 가 있으면 계산 없이 실패 기록 */
	private AnalysisRun finish(String id, String baseId, String mode, AnalysisRun.Video video, String model, String modelVersion,
			String thinking, Usage usage, int retries, String raw, AnalysisRun.Timings t, long started, String errorCode,
			String errorMessage) {
		ItemStandardCatalog.Snapshot table = catalog.snapshot();
		AnalysisRun.Catalog used = new AnalysisRun.Catalog(table.source(), table.loadedAt(), table.list().size(),
				table.aliases().size(), trucks.loadFactor());
		AnalysisRun.Cost cost = cost(usage);
		if (errorCode != null) {
			return new AnalysisRun(id, baseId, LocalDateTime.now(), mode, false, errorCode, errorMessage, video, model,
					modelVersion, thinking, AnalysisPrompt.VERSION, usage, cost, total(t, null, started), retries, raw, null,
					List.of(), List.of(), null, null, used);
		}
		long mapStart = System.currentTimeMillis();
		AiResultParser.Parsed parsed = AiResultParser.parse(raw);
		VolumeWeightService.Result result = volume.calculate(ItemMappingService.map(parsed.items(), table));
		TruckLoadService.Load load = trucks.estimate(result.totalCbm(), table.trucks());
		long mappingMs = System.currentTimeMillis() - mapStart;
		AnalysisRun.Ai ai = new AnalysisRun.Ai(parsed.videoOk(), parsed.rejectReason(), parsed.rooms(), parsed.memo());
		String code = null;
		String message = null;
		if (parsed.broken()) {
			code = "07";
			message = "AI 응답을 해석하지 못했어요.";
		} else if (!parsed.videoOk()) {
			code = parsed.rejectReason().matches(".*(어두|어둡|흔들|흐림|흐릿|화질|빛|밝기).*") ? "03" : "04";
			message = "AI 가 판단할 수 없는 영상이라고 답했어요: " + parsed.rejectReason();
		} else if (parsed.items().isEmpty()) {
			code = "04";
			message = "찾은 품목이 없어요.";
		}
		return new AnalysisRun(id, baseId, LocalDateTime.now(), mode, code == null, code, message, video, model, modelVersion,
				thinking, AnalysisPrompt.VERSION, usage, cost, total(t, mappingMs, started), retries, raw, ai, parsed.errors(),
				parsed.warnings(), result, load, used);
	}

	private static AnalysisRun.Timings total(AnalysisRun.Timings t, Long mappingMs, long started) {
		return new AnalysisRun.Timings(t.uploadMs(), t.saveMs(), t.aiFileMs(), t.apiMs(), mappingMs,
				System.currentTimeMillis() - started);
	}

	/** 입력 토큰 × 입력 단가 + (출력 + 생각) 토큰 × 출력 단가. 단가가 없으면 amount null */
	AnalysisRun.Cost cost(Usage usage) {
		VideoAnalysisProperties.Price p = properties.price();
		if (usage == null) {
			return null;
		}
		BigDecimal amount = null;
		if (p.set()) {
			amount = BigDecimal.valueOf(usage.promptTokens()).multiply(p.inputPerMillion())
					.add(BigDecimal.valueOf((long) usage.outputTokens() + usage.thoughtsTokens()).multiply(p.outputPerMillion()))
					.divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
		}
		return new AnalysisRun.Cost(p.inputPerMillion(), p.outputPerMillion(), p.currency(), p.asOf(), amount);
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
