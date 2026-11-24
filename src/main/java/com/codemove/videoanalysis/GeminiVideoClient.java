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

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Gemini generateContent 로 영상을 분석한다 (영상은 base64 인라인 전송, 20MB 이하).
 * 429 · 5xx · 연결 오류는 maxRetries 번까지 다시 시도한다 (1초 → 2초 …). 키는 서버 설정에만 두고 화면에 내보내지 않는다.
 */
@Slf4j
@Component
public class GeminiVideoClient {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};

	private final VideoAnalysisProperties.Gemini config;
	private final RestClient restClient;

	/** promptByModality: 입력 토큰을 종류별로 (VIDEO · AUDIO · TEXT …) */
	public record Usage(int promptTokens, int outputTokens, int thoughtsTokens, int totalTokens, int cachedTokens,
			Map<String, Integer> promptByModality) {
	}

	/** text: 모델이 돌려준 JSON 글 · retries: 다시 시도한 횟수 · apiMs: 마지막 성공 호출까지 걸린 전체 시간 */
	public record Response(String text, String modelVersion, Usage usage, int retries, long apiMs) {
	}

	/** code: NO_KEY · HTTP_4xx · HTTP_429 · HTTP_5xx · NETWORK · EMPTY · BLOCKED · IO */
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
		String data;
		try {
			data = Base64.getEncoder().encodeToString(Files.readAllBytes(video));
		} catch (IOException e) {
			throw new GeminiException("IO", "영상 파일을 읽지 못했어요: " + e.getMessage(), 0);
		}
		Map<String, Object> body = Map.of(
				"contents", List.of(Map.of("role", "user", "parts", List.of(
						Map.of("inline_data", Map.of("mime_type", mimeType, "data", data)),
						Map.of("text", prompt)))),
				"generationConfig", Map.of(
						"temperature", 0,
						"responseMimeType", "application/json",
						"responseSchema", AnalysisPrompt.responseSchema()));
		String url = config.baseUrl() + "/models/" + config.model() + ":generateContent";

		long start = System.currentTimeMillis();
		int retries = 0;
		while (true) {
			try {
				Map<String, Object> res = restClient.post().uri(url).header("x-goog-api-key", config.apiKey())
						.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JSON);
				return toResponse(res, retries, System.currentTimeMillis() - start);
			} catch (HttpStatusCodeException e) {
				int status = e.getStatusCode().value();
				boolean retryable = status == 429 || status >= 500;
				if (!retryable || retries >= config.maxRetries()) {
					String code = status == 429 ? "HTTP_429" : status >= 500 ? "HTTP_5xx" : "HTTP_4xx";
					throw new GeminiException(code, "Gemini 오류 " + status + ": " + shorten(e.getResponseBodyAsString()), retries);
				}
			} catch (ResourceAccessException e) {
				if (retries >= config.maxRetries()) {
					throw new GeminiException("NETWORK", "Gemini 연결 실패(시간 초과 등): " + e.getMessage(), retries);
				}
			}
			retries++;
			log.info("Gemini 다시 시도 {}회", retries);
			try {
				Thread.sleep(1000L << (retries - 1));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new GeminiException("NETWORK", "중단됨", retries);
			}
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
		return new Response(text.toString(), model, usage, retries, apiMs);
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
