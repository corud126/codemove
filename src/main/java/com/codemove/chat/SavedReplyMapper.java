package com.codemove.chat;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.codemove.domain.SavedReply;

/** 자주 쓰는 답변 (등록순, 순서 기능 없음) */
@Mapper
public interface SavedReplyMapper {

	List<SavedReply> selectAll();

	SavedReply selectById(Long replyId);

	/** REPLY_ID 는 SEQ_SAVED_REPLY */
	int insert(SavedReply reply);

	int update(SavedReply reply);

	int delete(Long replyId);
}
