package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 자주 쓰는 답변 (TBL_SAVED_REPLY, 등록순) */
@Getter
@Setter
public class SavedReply {

	private Long replyId;
	private String title;
	private String content;
	private LocalDateTime updatedAt;
	private Long adminId;
}
