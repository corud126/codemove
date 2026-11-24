package com.codemove.videoanalysis;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Gemini generateContent 로 영상을 분석한다.
 * 작은 영상(INLINE_MAX_BYTES 이하)은 base64 로 요청에 바로 넣고, 큰 영상은 Files API 로 올려 처리(ACTIVE)를 기다린 뒤 분석하고 바로 지운다.
 * 429 · 5xx · 연결 오류는 maxRetries 번까지 다시 시도한다 (1초 → 2초 …). 키는 서버 설정에만 두고 화면에 내보내지 않는다.
 */
@Slf4j
@Component
public class GeminiVideoClient {

	/** 요청 전체 20MB 한도 안에서 base64(약 1.33배) · 프롬프트를 고려한 인라인 기준 */
	public static final long INLINE_MAX_BYTES = 14L * 1024 * 1024;
	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};

	private final VideoAnalysisProperties.Gemini config;
	private final RestClient restClient;

	/** promptByModality: 입력 토큰을 종류별로 (VIDEO · AUDIO · TEXT …) */
	public record Usage(int promptTokens, int outputTokens, int thoughtsTokens, int totalTokens, int cachedTokens,
			Map<String, Integer> promptByModality) {
	}

	/**
	 * text: 모델이 돌려준 JSON 글 · retries: 다시 시도한 횟수 · apiMs: 분석 요청 시간(재시도 포함)
	 * fileUploadMs: Files API 업로드 + 처리 대기 시간 (인라인이면 0) · transfer: INLINE | FILE_API
	 */
	public record Response(String text, String modelVersion, Usage usage, int retries, long apiMs, long fileUploadMs,
			String transfer) {
	}

	/** code: NO_KEY · HTTP_4xx · HTTP_429 · HTTP_5xx · NETWORK · EMPTY · BLOCKED · IO · FILE */
	public static class GeminiException extends RuntimeException {

		private final String code;
		private final int retries;

		GeminiException(String code, String message, int retries) {
			super(message);
			this.code = code;
			this.retries = retries;
		}

		public String code() {
			return code;
		}

		public int retries() {
			return retries;
		}
	}

	public GeminiVideoClient(VideoAnalysisProperties properties) {
		this.config = properties.gemini();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
		factory.setReadTimeout(Duration.ofSeconds(config.timeoutSec()));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public boolean enabled() {
		return config.enabled();
	}

	public String model() {
		return config.model();
	}

	public Response analyze(Path video, String mimeType, String prompt) {
		if (!config.enabled()) {
			throw new GeminiException("NO_KEY", "Gemini API 키가 설정되지 않았어요.", 0);
		}
		long size;
		try {
			size = Files.size(video);
		} catch (IOException e) {
			throw new GeminiException("IO", "영상 파일을 읽지 못했어요: " + e.getMessage(), 0);
		}
		long uploadStart = System.currentTimeMillis();
		String remoteFile = null;
		Map<String, Object> videoPart;
		if (size <= INLINE_MAX_BYTES) {
			try {
				videoPart = Map.of("inline_data",
						Map.of("mime_type", mimeType, "data", Base64.getEncoder().encodeToString(Files.readAllBytes(video))));
			} catch (IOException e) {
				throw new GeminiException("IO", "영상 파일을 읽지 못했어요: " + e.getMessage(), 0);
			}
		} else {
			Map<?, ?> file = uploadFile(video, mimeType, size);
			remoteFile = String.valueOf(file.get("name"));
			waitActive(remoteFile, file);
			videoPart = Map.of("file_data", Map.of("mime_type", mimeType, "file_uri", String.valueOf(file.get("uri"))));
		}
		long fileUploadMs = remoteFile == null ? 0 : System.currentTimeMillis() - uploadStart;
		try {
			Map<String, Object> body = Map.of(
					"contents", List.of(Map.of("role", "user", "parts", List.of(videoPart, Map.of("text", prompt)))),
					"generationConfig", Map.of(
							"temperature", 0,
							"responseMimeType", "application/json",
							"responseSchema", AnalysisPrompt.responseSchema()));
			String url = config.baseUrl() + "/models/" + config.model() + ":generateContent";
			long start = System.currentTimeMillis();
			int[] retries = { 0 };
			Map<String, Object> res = withRetry(retries, () -> restClient.post().uri(url).header("x-goog-api-key", config.apiKey())
					.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JSON));
			Response r = toResponse(res, retries[0], System.currentTimeMillis() - start);
			return new Response(r.text(), r.modelVersion(), r.usage(), r.retries(), r.apiMs(), fileUploadMs,
					remoteFile == null ? "INLINE" : "FILE_API");
		} finally {
			if (remoteFile != null) {
				deleteQuietly(remoteFile);
			}
		}
	}

	/** Files API 재개 가능 업로드: 시작 요청으로 업로드 주소를 받고, 파일을 한 번에 올리며 마무리 */
	private Map<?, ?> uploadFile(Path video, String mimeType, long size) {
		String uploadBase = config.baseUrl().replaceFirst("/v1beta$", "/upload/v1beta") + "/files";
		try {
			ResponseEntity<Void> start = restClient.post().uri(uploadBase).header("x-goog-api-key", config.apiKey())
					.header("X-Goog-Upload-Protocol", "resumable").header("X-Goog-Upload-Command", "start")
					.header("X-Goog-Upload-Header-Content-Length", String.valueOf(size))
					.header("X-Goog-Upload-Header-Content-Type", mimeType).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("file", Map.of("display_name", video.getFileName().toString()))).retrieve().toBodilessEntity();
			String uploadUrl = start.getHeaders().getFirst("X-Goog-Upload-URL");
			if (uploadUrl == null) {
				throw new GeminiException("FILE", "업로드 주소를 받지 못했어요.", 0);
			}
			Map<String, Object> res = restClient.post().uri(java.net.URI.create(uploadUrl)).header("X-Goog-Upload-Offset", "0")
					.header("X-Goog-Upload-Command", "upload, finalize").contentLength(size)
					.contentType(MediaType.parseMediaType(mimeType)).body(new FileSystemResource(video)).retrieve().body(JSON);
			if (res == null || !(res.get("file") instanceof Map<?, ?> file) || file.get("name") == null) {
				throw new GeminiException("FILE", "업로드 응답에 파일 정보가 없어요: " + res, 0);
			}
			return file;
		} catch (HttpStatusCodeException e) {
			throw new GeminiException("FILE", "영상 업로드 실패 " + e.getStatusCode().value() + ": " + shorten(e.getResponseBodyAsString()), 0);
		} catch (ResourceAccessException e) {
			throw new GeminiException("NETWORK", "영상 업로드 연결 실패: " + e.getMessage(), 0);
		}
	}

	/** 업로드한 영상이 처리(PROCESSING → ACTIVE)될 때까지 2초마다 확인. 실패 · 시간 초과면 지우고 예외 */
	private void waitActive(String name, Map<?, ?> file) {
		long deadline = System.currentTimeMillis() + config.timeoutSec() * 1000L;
		Object state = file.get("state");
		while (!"ACTIVE".equals(state)) {
			if ("FAILED".equals(state) || System.currentTimeMillis() > deadline) {
				deleteQuietly(name);
				throw new GeminiException("FILE", "영상 처리 " + ("FAILED".equals(state) ? "실패" : "시간 초과") + " (" + name + ")", 0);
			}
			sleep(2000);
			try {
				Map<String, Object> res = restClient.get().uri(config.baseUrl() + "/" + name).header("x-goog-api-key", config.apiKey())
						.retrieve().body(JSON);
				state = res == null ? null : res.get("state");
			} catch (RestClientException e) {
				log.info("영상 처리 상태 확인 실패, 다시 확인: {}", e.getMessage());
			}
		}
	}

	private void deleteQuietly(String name) {
		try {
			restClient.delete().uri(config.baseUrl() + "/" + name).header("x-goog-api-key", config.apiKey()).retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			log.warn("Gemini 업로드 파일 삭제 실패 {} (48시간 뒤 자동 삭제)", name);
		}
	}

	/** 429 · 5xx · 연결 오류만 다시 시도. retries[0] 에 다시 시도한 횟수 */
	private <T> T withRetry(int[] retries, Supplier<T> call) {
		while (true) {
			try {
				return call.get();
			} catch (HttpStatusCodeException e) {
				int status = e.getStatusCode().value();
				boolean retryable = status == 429 || status >= 500;
				if (!retryable || retries[0] >= config.maxRetries()) {
					String code = status == 429 ? "HTTP_429" : status >= 500 ? "HTTP_5xx" : "HTTP_4xx";
					throw new GeminiException(code, "Gemini 오류 " + status + ": " + shorten(e.getResponseBodyAsString()), retries[0]);
				}
			} catch (ResourceAccessException e) {
				if (retries[0] >= config.maxRetries()) {
					throw new GeminiException("NETWORK", "Gemini 연결 실패(시간 초과 등): " + e.getMessage(), retries[0]);
				}
			}
			retries[0]++;
			log.info("Gemini 다시 시도 {}회", retries[0]);
			sleep(1000L << (retries[0] - 1));
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GeminiException("NETWORK", "중단됨", 0);
		}
	}

	private static Response toResponse(Map<String, Object> res, int retries, long apiMs) {
		if (res == null) {
			throw new GeminiException("EMPTY", "Gemini 응답이 비어 있어요.", retries);
		}
		Usage usage = usage(res.get("usageMetadata"));
		String model = res.get("modelVersion") == null ? "" : String.valueOf(res.get("modelVersion"));
		Object candidates = res.get("candidates");
		if (!(candidates instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) {
			Object feedback = res.get("promptFeedback");
			throw new GeminiException("BLOCKED", "응답 후보가 없어요: " + feedback, retries);
		}
		StringBuilder text = new StringBuilder();
		if (first.get("content") instanceof Map<?, ?> content && content.get("parts") instanceof List<?> parts) {
			for (Object p : parts) {
				if (p instanceof Map<?, ?> part && part.get("text") != null && !Boolean.TRUE.equals(part.get("thought"))) {
					text.append(part.get("text"));
				}
			}
		}
		if (text.isEmpty()) {
			throw new GeminiException("EMPTY", "응답에 글이 없어요 (finishReason " + first.get("finishReason") + ").", retries);
		}
		return new Response(text.toString(), model, usage, retries, apiMs, 0, "");
	}

	private static Usage usage(Object meta) {
		if (!(meta instanceof Map<?, ?> m)) {
			return new Usage(0, 0, 0, 0, 0, Map.of());
		}
		Map<String, Integer> byModality = new LinkedHashMap<>();
		if (m.get("promptTokensDetails") instanceof List<?> details) {
			for (Object d : details) {
				if (d instanceof Map<?, ?> detail) {
					byModality.put(String.valueOf(detail.get("modality")), number(detail.get("tokenCount")));
				}
			}
		}
		return new Usage(number(m.get("promptTokenCount")), number(m.get("candidatesTokenCount")),
				number(m.get("thoughtsTokenCount")), number(m.get("totalTokenCount")), number(m.get("cachedContentTokenCount")),
				Map.copyOf(byModality));
	}

	private static int number(Object value) {
		return value instanceof Number n ? n.intValue() : 0;
	}

	private static String shorten(String s) {
		return s == null ? "" : s.length() > 500 ? s.substring(0, 500) + "…" : s;
	}
}
