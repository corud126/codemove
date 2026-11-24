package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.codemove.videoanalysis.GeminiVideoClient.Usage;

/**
 * 분석 1회 기록 (가이드 10장 필수 항목, 나중에 TBL_AI_ANALYSIS 한 행으로 옮긴다). JSON 파일로 저장해 반복 테스트 · 비교에 쓴다.
 * mode: GEMINI(영상 분석) · MANUAL(JSON 직접 입력, 수정 후 재계산) · baseRunId: 다시 분석 · 재계산의 원래 실행
 * errorCode: 공통코드 ANALYSIS_ERROR (01 형식 · 02 용량/길이 · 03 품질 · 04 집 아님/품목 없음 · 05 시간 초과 · 06 한도
 * · 07 응답 형식 · 08 호출 실패 · 09 키 없음 · 10 서버 혼잡) · rawResponse: AI 원본 글 (정규화 결과 result 와 따로)
 * model: 실제로 쓴 모델(예비 모델로 넘어가면 그 모델) · thinkingLevel: 보낸 생각 수준 · fps: 영상 초당 프레임(null 이면 Gemini 기본 1)
 */
public record AnalysisRun(String id, String baseRunId, LocalDateTime createdAt, String mode, boolean success,
		String errorCode, String errorMessage, Video video, String model, String modelVersion, String thinkingLevel, Double fps,
		String promptVersion, Usage usage, Cost cost, Timings timings, int retries, String rawResponse, Ai ai,
		List<String> parseErrors, List<String> parseWarnings, VolumeWeightService.Result result, TruckLoadService.Load load,
		Catalog catalog) {

	/** 공통코드 ANALYSIS_ERROR 이름 */
	public static final Map<String, String> ERRORS = Map.of("01", "파일 형식 오류", "02", "용량·길이 초과", "03",
			"영상 품질 불량(어둡거나 흔들림)", "04", "집 안 영상 아님(품목 없음)", "05", "AI 시간 초과", "06", "AI 사용량 한도", "07",
			"AI 응답 형식 오류", "08", "AI 호출 실패", "09", "AI 키 없음", "10", "AI 서버 혼잡");

	/** storedName: runDir/videos 안의 파일 이름 · transfer: INLINE | FILE_API */
	public record Video(String fileName, long sizeBytes, Double durationSec, String mimeType, String storedName,
			String transfer) {
	}

	/** AI 가 영상 전체에 대해 답한 것: 판단 가능 여부 · 거절 이유 · 본 공간 · 방문 견적자 메모 */
	public record Ai(boolean videoOk, String rejectReason, List<String> rooms, String memo, AiResultParser.VideoState state) {
	}

	/** amount 가 null 이면 단가 미설정 */
	public record Cost(BigDecimal inputPerMillion, BigDecimal outputPerMillion, String currency, String asOf,
			BigDecimal amount) {
	}

	/** 업로드(브라우저 → 서버) · 저장 · AI 파일 전송(Files API 업로드 + 처리 대기, 인라인이면 0) · API · 매핑+계산 · 서버 전체 (밀리초, 모르면 null) */
	public record Timings(Long uploadMs, Long saveMs, Long aiFileMs, Long apiMs, Long mappingMs, Long totalMs) {
	}

	/** 사용한 기준 데이터 (품목 기준표 경로 · 읽은 시각 · 규격 행 수 · 별칭 수 · 적재계수) */
	public record Catalog(String source, LocalDateTime loadedAt, int rows, int aliases, BigDecimal loadFactor) {
	}

	public String errorName() {
		return errorCode == null ? null : ERRORS.getOrDefault(errorCode, errorCode);
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
