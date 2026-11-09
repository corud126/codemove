package com.codemove.domain;

import lombok.Getter;
import lombok.Setter;

/** 퀵메뉴 (TBL_QUICK_MENU, 최대 5개) */
@Getter
@Setter
public class QuickMenu {

	public static final int MAX = 5;

	private Long menuId;
	private Long topicId;
	/** 버튼 문구 */
	private String menuName;
	private Integer sortOrder;
	private String isActive;
	private Long adminId;

	/** 연결 주제 사용 여부 (고객 화면에는 주제도 사용 중인 것만) */
	private String topicActive;
}
