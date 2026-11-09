package com.codemove.request;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.codemove.domain.MoveRequest;
import com.codemove.domain.ReVideo;
import com.codemove.domain.RequestItem;

@Mapper
public interface MoveRequestMapper {

	/** REQUEST_ID 는 SEQ_MOVE_REQUEST */
	int insertRequest(MoveRequest request);

	int insertVideo(ReVideo video);

	int insertItem(RequestItem item);

	MoveRequest selectById(Long requestId);

	List<RequestItem> selectItems(Long requestId);

	ReVideo selectVideo(Long requestId);

	/** 견적 삭제 시 요청까지 지울 때: 품목 → 영상 → 요청 순서 (FK) */
	int deleteItems(Long requestId);

	int deleteVideo(Long requestId);

	int deleteRequest(Long requestId);
}
