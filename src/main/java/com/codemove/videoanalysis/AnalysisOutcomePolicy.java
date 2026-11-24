package com.codemove.videoanalysis;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.codemove.videoanalysis.AiResultParser.VideoState;

/**
 * 분석 결과 → 고객에게 보일 안내 · 버튼 · 관리자 표시 (분석이 어려운 영상 처리 규칙).
 * 화면 코드와 분리해 두고, 테스트 화면 미리보기와 나중의 고객 견적 흐름이 같이 쓴다.
 *
 * 판정 (팀 AI_VIDEO_ESTIMATE.md 6장 + 부분 분석 추가)
 * - RETAKE 다시 찍기: 업로드 형식 01 · 용량/길이 02 · 품질 불량 03(어두움 · 흔들림 …) · 집 안 아님/짐 안 보임 04
 * - RETRY_LATER 잠시 후 다시: AI 쪽 문제 05 시간 초과 · 06 한도 · 07 응답 형식 · 08 호출 실패 · 09 키 없음 · 10 혼잡 (영상은 저장돼 있어 다시 올릴 필요 없음)
 * - PARTIAL 일부만 보임: 분석은 됐지만 coverage 가 일부 · 거의없음, 찾은 품목 수가 적음, 본 공간이 1곳, 확인 필요 비율이 높음
 * - OK 정상
 * 다시 찍기 · 재분석은 maxRetries 번까지 (처음 1회 + 재시도). 다 쓰면 품목 직접 입력 · 방문 견적 요청만 남는다.
 */
@Component
public class AnalysisOutcomePolicy {

	public enum Type {
		OK, PARTIAL, RETAKE, RETRY_LATER
	}

	/** key: 화면 동작 이름 (retake · reanalyze · add-video · add-item · manual · visit · next) · primary: 주 버튼 */
	public record Action(String key, String label, boolean primary) {
	}

	/**
	 * reason: 판정 이유 코드 (ANALYSIS_ERROR 01~10 또는 P1 범위 일부 · P2 품목 적음 · P3 공간 1곳 · P4 확인 필요 많음)
	 * tips: 다시 찍을 때 요령 · adminLabel: 관리자 요청 목록 표시 · attemptsLeft: 남은 다시 찍기 · 재분석 횟수
	 */
	public record Outcome(Type type, String reason, String title, String message, List<String> tips, List<Action> actions,
			String adminLabel, int attemptsUsed, int attemptsLeft) {
	}

	static final List<String> SHOOTING_TIPS = List.of("1~3분 길이로 찍어 주세요.", "불을 모두 켜고 찍어 주세요.",
			"방마다 천천히 한 바퀴 돌며 찍어 주세요.", "문이 닫힌 수납장 · 붙박이장은 열어서 보여 주세요.");

	private final int maxRetries;
	private final int minItems;
	private final double reviewRatio;

	public AnalysisOutcomePolicy(@Value("${codemove.video-analysis.outcome.max-retries:2}") int maxRetries,
			@Value("${codemove.video-analysis.outcome.min-items:3}") int minItems,
			@Value("${codemove.video-analysis.outcome.review-ratio:0.5}") double reviewRatio) {
		this.maxRetries = maxRetries;
		this.minItems = minItems;
		this.reviewRatio = reviewRatio;
	}

	/** 업로드 검증에서 막힌 경우 (01 · 02). attemptsUsed: 지금까지 분석을 시도한 횟수 */
	public Outcome forUploadReject(String code, String message, int attemptsUsed) {
		String title = "01".equals(code) ? "동영상 파일만 올릴 수 있어요" : "영상 길이나 용량이 맞지 않아요";
		return retake(code, title, message, attemptsUsed, "업로드 형식 · 길이 오류");
	}

	/** 분석 기록으로 판정. attemptsUsed: 이번까지 포함한 분석 시도 횟수 (처음이면 1) */
	public Outcome evaluate(AnalysisRun run, int attemptsUsed) {
		Outcome o = decide(run, attemptsUsed);
		// 예비 모델로 분석된 결과는 개수를 적게 세는 경향 → 고객 안내는 그대로, 관리자에게만 표시
		boolean fallback = run.parseWarnings() != null && run.parseWarnings().stream().anyMatch(w -> w.contains("예비 모델"));
		return fallback ? new Outcome(o.type(), o.reason(), o.title(), o.message(), o.tips(), o.actions(), o.adminLabel() + " · 예비 모델",
				o.attemptsUsed(), o.attemptsLeft()) : o;
	}

	private Outcome decide(AnalysisRun run, int attemptsUsed) {
		String code = run.errorCode();
		AiResultParser.VideoState state = run.ai() == null || run.ai().state() == null ? VideoState.NONE : run.ai().state();
		if ("03".equals(code)) {
			return retake(code, qualityTitle(state.qualityIssues(), run.ai() == null ? "" : run.ai().rejectReason()),
					"짐이 잘 보이도록 다시 찍어 주세요.", attemptsUsed, "영상 품질 불량 (" + issues(state, run) + ")");
		}
		if ("04".equals(code)) {
			return retake(code, "집 안 짐이 잘 보이지 않아요", "가구 · 가전이 화면에 담기도록 방마다 찍어 주세요.", attemptsUsed,
					"집 안 영상 아님 · 짐 없음");
		}
		if (code != null) {
			int left = Math.max(0, maxRetries - (attemptsUsed - 1));
			List<Action> actions = new ArrayList<>();
			if (left > 0) {
				actions.add(new Action("reanalyze", "다시 분석하기", true));
			}
			actions.add(new Action("manual", "품목 직접 입력하기", left == 0));
			if (left == 0) {
				actions.add(new Action("visit", "방문 견적 요청하기", false));
			}
			String title = "06".equals(code) || "10".equals(code) ? "지금 분석이 몰려 있어요" : "분석을 마치지 못했어요";
			String message = left > 0 ? "영상은 저장됐어요. 잠시 후 다시 분석해 주세요." : "품목을 직접 넣거나 방문 견적을 요청해 주세요.";
			return new Outcome(Type.RETRY_LATER, code, title, message, List.of(), List.copyOf(actions),
					"AI 분석 실패 (" + AnalysisRun.ERRORS.getOrDefault(code, code) + ")", attemptsUsed, left);
		}
		// 분석은 됐다: 일부만 보였는지
		List<String> reasons = new ArrayList<>();
		int items = run.itemCount();
		int review = run.reviewCount();
		int rooms = run.ai() == null ? 0 : run.ai().rooms().size();
		if ("일부".equals(state.coverage()) || "거의없음".equals(state.coverage())) {
			reasons.add("P1");
		}
		if (items < minItems) {
			reasons.add("P2");
		}
		if (rooms == 1) {
			reasons.add("P3");
		}
		if (items > 0 && (double) review / items >= reviewRatio) {
			reasons.add("P4");
		}
		int left = Math.max(0, maxRetries - (attemptsUsed - 1));
		if (reasons.isEmpty()) {
			return new Outcome(Type.OK, "", "짐을 확인해 주세요", "정보가 다르면 품목을 눌러 고치고, 빠진 짐은 추가해 주세요.", List.of(),
					List.of(new Action("next", "확인했어요", true), new Action("add-item", "빠진 품목 추가", false)),
					review > 0 ? "AI 분석 완료 · 확인 필요 " + review + "개" : "AI 분석 완료", attemptsUsed, left);
		}
		String unseen = state.unseenAreas().isEmpty() ? "" : " (" + String.join(", ", state.unseenAreas()) + ")";
		List<Action> actions = new ArrayList<>();
		if (left > 0) {
			actions.add(new Action("add-video", "영상 추가하기", true));
		}
		actions.add(new Action("add-item", "빠진 품목 추가", left == 0));
		actions.add(new Action("next", "이대로 진행", false));
		String message = reasons.contains("P4") && reasons.size() == 1 ? "확인이 필요한 품목이 많아요. 눌러서 종류와 크기를 확인해 주세요."
				: "찾은 짐을 확인하고, 빠진 방은 영상을 추가하거나 직접 넣어 주세요." + unseen;
		String title = reasons.contains("P4") && reasons.size() == 1 ? "확인이 필요한 품목이 많아요" : "일부 공간만 보였어요";
		return new Outcome(Type.PARTIAL, String.join(",", reasons), title, message, List.of(), List.copyOf(actions),
				"AI 일부 분석 (" + String.join(", ", reasons.stream().map(AnalysisOutcomePolicy::partialName).toList()) + ")" + unseen,
				attemptsUsed, left);
	}

	private Outcome retake(String code, String title, String message, int attemptsUsed, String adminLabel) {
		int left = Math.max(0, maxRetries - (attemptsUsed - 1));
		List<Action> actions = new ArrayList<>();
		if (left > 0) {
			actions.add(new Action("retake", "다시 찍어 올리기", true));
		}
		actions.add(new Action("manual", "품목 직접 입력하기", left == 0));
		if (left == 0) {
			actions.add(new Action("visit", "방문 견적 요청하기", false));
		}
		String msg = left > 0 ? message : "다시 찍기를 모두 사용했어요. 품목을 직접 넣거나 방문 견적을 요청해 주세요.";
		return new Outcome(Type.RETAKE, code, title, msg, left > 0 ? SHOOTING_TIPS : List.of(), List.copyOf(actions), adminLabel,
				attemptsUsed, left);
	}

	/** 품질 문제에 맞는 제목 (AI 가 고른 문제 → 없으면 거절 이유 글에서) */
	static String qualityTitle(List<String> issues, String reason) {
		String all = String.join(" ", issues) + " " + (reason == null ? "" : reason);
		if (all.matches(".*(어두|어둡|빛).*")) {
			return "영상이 어두워서 짐을 확인하기 어려워요";
		}
		if (all.matches(".*(흔들|빠름|빠르).*")) {
			return "화면이 많이 흔들려서 짐을 확인하기 어려워요";
		}
		if (all.matches(".*(흐림|흐릿|초점).*")) {
			return "화면이 흐려서 짐을 확인하기 어려워요";
		}
		return "영상으로 짐을 확인하기 어려워요";
	}

	private static String issues(VideoState state, AnalysisRun run) {
		if (!state.qualityIssues().isEmpty()) {
			return String.join(" · ", state.qualityIssues());
		}
		return run.ai() == null || run.ai().rejectReason().isBlank() ? "이유 없음" : run.ai().rejectReason();
	}

	private static String partialName(String code) {
		return switch (code) {
			case "P1" -> "일부 공간만";
			case "P2" -> "품목 적음";
			case "P3" -> "공간 1곳";
			case "P4" -> "확인 필요 많음";
			default -> code;
		};
	}
}
