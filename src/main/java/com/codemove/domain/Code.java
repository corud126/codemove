package com.codemove.domain;

import lombok.Getter;
import lombok.Setter;

/** 공통코드 (TBL_CODE) */
@Getter
@Setter
public class Code {

	private String groupCode;
	private String code;
	private String codeName;
	/** 사용자앱 표시명. 없으면 CODE_NAME 을 쓴다 */
	private String userName;
	private int sortOrder;

	public String getDisplayName() {
		return userName != null ? userName : codeName;
	}
}
