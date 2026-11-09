package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 견적 요청 영상 (TBL_RE_VIDEO) */
@Getter
@Setter
public class ReVideo {

	private Long videoId;
	private Long requestId;
	/** 사용자가 올린 원래 파일 이름 */
	private String fileName;
	/** 서버 저장 경로 */
	private String filePath;
	private LocalDateTime uploadedAt;
}
