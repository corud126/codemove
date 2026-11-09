package com.codemove.request;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * 영상 속 가구·짐 인식 (AI 모듈 연동 지점).
 * AI 모듈이 준비되면 이 인터페이스의 구현체를 추가하고 DummyVideoAnalyzer 를 끈다.
 */
public interface VideoAnalyzer {

	/** name: 품목 이름(ItemCatalog 기준), sizeCode: ITEM_SIZE, confidence: 0~100 */
	record Detected(String name, String sizeCode, int quantity, int confidence) implements Serializable {
	}

	List<Detected> analyze(Path video);
}
