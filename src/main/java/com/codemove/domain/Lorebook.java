package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 로어북 (TBL_LOREBOOK): 안내형 주제의 키워드별 답변 재료 */
@Getter
@Setter
public class Lorebook {

	private Long lorebookId;
	private Long topicId;
	/** "#견적#비용#가격" */
	private String keywords;
	/** AI(챗봇)에게 전달할 내용 */
	private String content;
	private Integer sortOrder;
	private LocalDateTime updatedAt;
}
