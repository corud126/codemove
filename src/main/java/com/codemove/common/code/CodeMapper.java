package com.codemove.common.code;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.codemove.domain.Code;

@Mapper
public interface CodeMapper {

	/** 사용 중인 그룹의 사용 중인 코드 전체 (그룹·정렬 순) */
	List<Code> selectActiveCodes();
}
