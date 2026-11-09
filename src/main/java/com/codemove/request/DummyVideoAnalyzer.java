package com.codemove.request;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** 임시: AI 모듈 연동 전까지 화면 시안과 같은 인식 결과를 돌려준다 (소파는 신뢰도가 낮아 '확인 필요') */
@Component
@ConditionalOnMissingBean(value = VideoAnalyzer.class, ignored = DummyVideoAnalyzer.class)
public class DummyVideoAnalyzer implements VideoAnalyzer {

	@Override
	public List<Detected> analyze(Path video) {
		return List.of(
				new Detected("냉장고", "02", 1, 96),
				new Detected("침대", "02", 1, 94),
				new Detected("소파", "02", 1, 62),
				new Detected("이사박스", "02", 13, 91),
				new Detected("세탁기", "02", 1, 95),
				new Detected("테이블", "01", 1, 88));
	}
}
