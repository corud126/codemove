package com.codemove.chat;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.ChatMessage;
import com.codemove.domain.ChatRoom;
import com.codemove.domain.ChatSetting;

/** 채팅방 · 메시지 · 채팅 운영 설정 */
@Mapper
public interface ChatMapper {

	/* ---------- 채팅방 ---------- */

	/** 회원의 가장 최근 방 (종료된 방 포함). 없으면 null */
	ChatRoom selectLatestRoom(Long memberId);

	ChatRoom selectRoom(Long roomId);

	/** 회원의 모든 방 (ROOM_ID 순) — 관리자 채팅방에서 대기 시작 · 종료 줄을 그린다 */
	List<ChatRoom> selectRoomsOfMember(Long memberId);

	/** ROOM_ID 는 SEQ_CHAT_ROOM */
	int insertRoom(ChatRoom room);

	/** 챗봇 응대 → 상담 대기 (WAITING_AT 기록) */
	int updateWaiting(Long roomId);

	/** 대기 · 챗봇 응대 → 상담 중. 담당자가 없으면 adminId 로 정한다 */
	int updateActive(@Param("roomId") Long roomId, @Param("adminId") Long adminId);

	/** 상담 종료 (STATUS 04 · ENDED_AT). 담당자가 없으면 adminId (자동 종료는 NULL) */
	int updateEnded(@Param("roomId") Long roomId, @Param("adminId") Long adminId);

	/**
	 * 무응답 자동 종료: 챗봇 응대 · 상담 중인 방에서 마지막 메시지(없으면 시작)가 minutes 분보다 오래되면 종료.
	 * memberId 가 있으면 그 회원의 방만.
	 */
	int endIdleRooms(@Param("minutes") int minutes, @Param("memberId") Long memberId);

	/** 상담함: 회원마다 가장 최근 방 한 줄 (이름 · 마지막 메시지 · 안 읽은 수), 최근 활동 순 */
	List<ChatRoom> selectLatestRooms();

	/** 상담함 검색: 이름 · 휴대폰(숫자 3자리 이상) · 대화 내용이 맞는 회원 */
	List<Long> searchMemberIds(@Param("query") String query, @Param("digits") String digits);

	/** 지연 안내를 보낼 대기 방: WAITING_AT 이 minutes 분보다 오래됐고, 그 뒤로 관리자 답장도 같은 안내도 없는 방 */
	List<ChatRoom> selectOverdueWaitingRooms(@Param("minutes") int minutes, @Param("notice") String notice,
			@Param("memberId") Long memberId);

	/* ---------- 메시지 ---------- */

	/** MESSAGE_ID 는 SEQ_CHAT_MESSAGE */
	int insertMessage(ChatMessage message);

	List<ChatMessage> selectRoomMessages(Long roomId);

	/** 회원의 방들에서 afterId 보다 뒤의 메시지 (사용자 채팅 폴링) */
	List<ChatMessage> selectMemberMessagesAfter(@Param("memberId") Long memberId, @Param("afterId") long afterId);

	/** 회원의 최근 메시지 limit 개 (MESSAGE_ID 내림차순) — 관리자 채팅방 */
	List<ChatMessage> selectRecentMemberMessages(@Param("memberId") Long memberId, @Param("limit") int limit);

	/** 첨부 내려받기 권한 확인용: 메시지가 속한 방의 회원 */
	Long selectMessageMemberId(Long messageId);

	ChatMessage selectMessage(Long messageId);

	/** senderType 이 보낸 메시지를 읽음 처리 (관리자가 열면 고객 메시지, 고객이 열면 관리자 메시지) */
	int markRead(@Param("memberId") Long memberId, @Param("senderType") String senderType);

	/* ---------- 운영 설정 (한 줄) ---------- */

	ChatSetting selectSetting();

	/** SETTING_ID 는 SEQ_CHAT_SETTING */
	int insertSetting(ChatSetting setting);

	int updateSetting(ChatSetting setting);

	/* ---------- 챗봇 조회형 답변 · 고객 정보 (견적 · 예약은 읽기만) ---------- */

	MemberReservation selectMemberReservation(Long memberId);

	MemberQuote selectMemberQuote(Long memberId);
}
