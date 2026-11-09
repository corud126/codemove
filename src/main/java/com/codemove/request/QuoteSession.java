package com.codemove.request;

import java.io.Serializable;
import java.util.List;

/** 견적 요청 작성 중 세션에 두는 값: 올린 영상 · AI 인식 결과 (요청을 내면 지운다) */
public record QuoteSession(String videoName, String videoPath, List<VideoAnalyzer.Detected> detected,
		Integer analysisSec) implements Serializable {

	public static final String KEY = "quoteSession";

	public QuoteSession analyzed(List<VideoAnalyzer.Detected> result, int seconds) {
		return new QuoteSession(videoName, videoPath, List.copyOf(result), seconds);
	}
}
