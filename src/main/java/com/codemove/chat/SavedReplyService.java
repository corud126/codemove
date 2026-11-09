package com.codemove.chat;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.SavedReply;

import lombok.RequiredArgsConstructor;

/** 자주 쓰는 답변 (채팅 설정 › 자주 쓰는 답변, 채팅방 답변 시트) */
@Service
@RequiredArgsConstructor
public class SavedReplyService {

	private final SavedReplyMapper savedReplyMapper;

	public List<SavedReply> list() {
		return savedReplyMapper.selectAll();
	}

	/** replyId 가 없으면 추가, 있으면 수정 */
	@Transactional
	public SavedReply save(Long replyId, String title, String content, Long adminId) {
		String t = title == null ? "" : title.strip();
		String c = content == null ? "" : content.strip();
		if (t.isEmpty() || t.length() > 30) {
			throw new IllegalArgumentException("제목을 다시 확인해 주세요.");
		}
		if (c.isEmpty() || c.length() > 500) {
			throw new IllegalArgumentException("내용을 다시 확인해 주세요.");
		}
		SavedReply reply = replyId != null ? savedReplyMapper.selectById(replyId) : new SavedReply();
		if (reply == null) {
			throw new IllegalArgumentException("이미 삭제된 답변입니다.");
		}
		reply.setTitle(t);
		reply.setContent(c);
		reply.setAdminId(adminId);
		if (reply.getReplyId() == null) {
			savedReplyMapper.insert(reply);
		} else {
			savedReplyMapper.update(reply);
		}
		return reply;
	}

	@Transactional
	public void delete(Long replyId) {
		savedReplyMapper.delete(replyId);
	}
}
