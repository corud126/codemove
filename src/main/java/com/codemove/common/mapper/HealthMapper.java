package com.codemove.common.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HealthMapper {

	@Select("SELECT BANNER FROM V$VERSION WHERE ROWNUM = 1")
	String selectDbVersion();

	@Select("SELECT USER FROM DUAL")
	String selectCurrentUser();

	@Select("SELECT TABLE_NAME FROM USER_TABLES ORDER BY TABLE_NAME")
	List<String> selectTableNames();

	@Select("SELECT SEQUENCE_NAME FROM USER_SEQUENCES ORDER BY SEQUENCE_NAME")
	List<String> selectSequenceNames();
}
