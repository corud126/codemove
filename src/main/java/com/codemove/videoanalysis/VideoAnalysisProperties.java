package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 영상 분석 테스트 설정 (codemove.video-analysis). 키 · 단가는 application-local.yml 에만 적는다.
 * runDir: 테스트 영상 · 실행 기록 저장 폴더
 * 영상 한도는 팀 기준(AI_VIDEO_ESTIMATE.md 6장): 200MB · 5초~5분. 14MB 이하는 인라인, 넘으면 Gemini Files API.
 * 기준 파일 경로(standard-csv · alias-csv · truck-csv · testset-dir)와 적재계수(load-factor)는 각 서비스가 직접 읽는다.
 */
@ConfigurationProperties("codemove.video-analysis")
public record VideoAnalysisProperties(String runDir, Integer maxVideoMb, Integer minDurationSec, Integer maxDurationSec,
		List<String> allowedExtensions, Gemini gemini, Price price) {

	public VideoAnalysisProperties {
		runDir = runDir == null || runDir.isBlank() ? "uploads/video-analysis" : runDir;
		maxVideoMb = maxVideoMb == null ? 200 : maxVideoMb;
		minDurationSec = minDurationSec == null ? 5 : minDurationSec;
		maxDurationSec = maxDurationSec == null ? 300 : maxDurationSec;
		allowedExtensions = allowedExtensions == null || allowedExtensions.isEmpty() ? List.of("mp4", "mov", "webm")
				: allowedExtensions;
		gemini = gemini == null ? new Gemini(null, null, null, null, null, null, null) : gemini;
		price = price == null ? new Price(null, null, null, null) : price;
	}

	/**
	 * apiKey 가 비어 있으면 영상 분석은 막고 JSON 직접 입력만 된다.
	 * thinkingLevel: 생각 수준 (팀 기준 low, 빈 값이면 보내지 않음) · fallbackModels: 주 모델이 혼잡(503) · 한도(429)일 때 차례로 쓸 모델
	 */
	public record Gemini(String apiKey, String model, List<String> fallbackModels, String thinkingLevel, String baseUrl,
			Integer timeoutSec, Integer maxRetries) {

		public Gemini {
			apiKey = apiKey == null ? "" : apiKey.trim();
			model = model == null || model.isBlank() ? "gemini-4-hydrogen" : model.trim();
			fallbackModels = fallbackModels == null ? List.of()
					: fallbackModels.stream().map(String::trim).filter(m -> !m.isEmpty()).toList();
			thinkingLevel = thinkingLevel == null ? "low" : thinkingLevel.trim();
			baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://generativelanguage.googleapis.com/v1beta" : baseUrl;
			timeoutSec = timeoutSec == null ? 300 : timeoutSec;
			maxRetries = maxRetries == null ? 2 : maxRetries;
		}

		public boolean enabled() {
			return !apiKey.isEmpty();
		}
	}

	/**
	 * 100만 토큰당 단가 (공식 요금표 기준, 설정하지 않으면 비용은 '단가 미설정'). asOf: 단가 기준일
	 * 출력 단가는 생각(thinking) 토큰에도 적용한다.
	 */
	public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion, String currency, String asOf) {

		public Price {
			currency = currency == null || currency.isBlank() ? "USD" : currency;
			asOf = asOf == null ? "" : asOf;
		}

		public boolean set() {
			return inputPerMillion != null && outputPerMillion != null;
		}
	}
}
