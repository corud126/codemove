package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.codemove.videoanalysis.GeminiVideoClient.Usage;

/**
 * 분석 1회 기록 (가이드 10장 필수 항목). JSON 파일로 저장해 반복 테스트 · 모델/프롬프트별 비교에 쓴다.
 * mode: GEMINI(영상 분석) · MANUAL(JSON 직접 입력, 수정 후 재계산) · baseRunId: MANUAL 재계산의 원래 실행
 * rawResponse: AI 원본 글 (정규화 결과 result 와 따로 둔다)
 */
public record AnalysisRun(String id, String baseRunId, LocalDateTime createdAt, String mode, boolean success,
		String errorCode, String errorMessage, Video video, String model, String modelVersion, String promptVersion,
		Usage usage, Cost cost, Timings timings, int retries, String rawResponse, List<String> parseErrors,
		List<String> parseWarnings, VolumeWeightService.Result result, Catalog catalog) {

	/** storedName: runDir/videos 안의 파일 이름 (삭제하면 null) */
	public record Video(String fileName, long sizeBytes, Double durationSec, String mimeType, String storedName) {
	}

	/** amount 가 null 이면 단가 미설정 */
	public record Cost(BigDecimal inputPerMillion, BigDecimal outputPerMillion, String currency, String asOf,
			BigDecimal amount) {
	}

	/** 업로드(브라우저 → 서버, 브라우저가 잰 값) · 저장 · API · 매핑+계산 · 서버 전체 (밀리초, 모르면 null) */
	public record Timings(Long uploadMs, Long saveMs, Long apiMs, Long mappingMs, Long totalMs) {
	}

	/** 사용한 기준표 (파일 경로 · 읽은 시각 · 행 수) */
	public record Catalog(String source, LocalDateTime loadedAt, int rows) {
	}

	public int itemCount() {
		return result == null ? 0 : result.lines().size();
	}

	public long mappedCount() {
		return result == null ? 0 : result.lines().stream().filter(l -> l.item().standard() != null).count();
	}

	public int reviewCount() {
		return result == null ? 0 : result.reviewCount();
	}
}
