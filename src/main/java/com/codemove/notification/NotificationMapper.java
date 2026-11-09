package com.codemove.notification;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.Notification;
import com.codemove.domain.NotificationSetting;

/**
 * 알림 · 알림 설정. 관리자 알림 받기(TBL_ADMIN.PUSH_ENABLED)와
 * D-3 대상 예약(TBL_RESERVATION · TBL_MOVE_REQUEST, 읽기 전용)도 여기서 조회한다.
 */
@Mapper
public interface NotificationMapper {

	/** NOTIFICATION_ID 는 SEQ_NOTIFICATION */
	int insert(Notification notification);

	// ==================== 회원 ====================

	/** 회원 알림 (최근 순, 최대 limit 건) */
	List<Notification> selectForMember(@Param("memberId") Long memberId, @Param("limit") int limit);

	int countUnreadForMember(Long memberId);

	/** 회원 본인 알림만 읽음 처리 */
	int markReadForMember(@Param("notificationId") Long notificationId, @Param("memberId") Long memberId);

	int markAllReadForMember(Long memberId);

	/** 휴대폰 메시지를 받을 번호 (활성 회원만, 없으면 null) */
	String selectMemberPhone(Long memberId);

	// ==================== 관리자 알림 패널 (RECEIVER_TYPE 02, 관리자 공용) ====================

	/** 최근 days 일, 최근 순, 최대 limit 건 */
	List<Notification> selectAdminPanel(@Param("days") int days, @Param("limit") int limit);

	int countAdminUnread(int days);

	int markAdminRead(Long notificationId);

	int markAllAdminRead();

	/** 알림 받기(PUSH_ENABLED = 'Y')인 활성 관리자 휴대폰 */
	List<String> selectPushPhones();

	String selectPushEnabled(Long adminId);

	int updatePushEnabled(@Param("adminId") Long adminId, @Param("pushEnabled") String pushEnabled);

	// ==================== 관리자 발송 내역 (RECEIVER_TYPE 01) ====================

	/** 조건에 맞는 다음 페이지 (NOTIFICATION_ID 내림차순, cursor 보다 작은 것) — limit 건 */
	List<Notification> selectHistory(@Param("q") HistoryQuery query, @Param("cursor") Long cursor, @Param("limit") int limit);

	int countHistory(@Param("q") HistoryQuery query);

	/** 오늘 발송 (SENT_AT 오늘) */
	int countSentToday();

	/** 보류 중 (SENT_AT NULL) */
	int countHeld();

	/** 최근 days 일 문자 대체 */
	int countSmsFallback(int days);

	// ==================== 야간 보류 발송 ====================

	/** 보류 중인 회원 알림 (오래된 순) */
	List<Notification> selectHeld();

	/** 발송 처리: SENT_AT = SYSDATE, 실제 발송 경로 기록 */
	int markSent(@Param("notificationId") Long notificationId, @Param("channel") String channel);

	/** 발송 경로만 바꿈 (알림톡 실패 → 문자 대체) */
	int updateChannel(@Param("notificationId") Long notificationId, @Param("channel") String channel);

	// ==================== 설정 ====================

	NotificationSetting selectSetting();

	int insertSetting(NotificationSetting setting);

	int updateSetting(NotificationSetting setting);

	// ==================== D-3 ====================

	/** 이사일이 오늘 + days 인 예약 확정(01) 건 중 아직 D-3 알림을 받지 않은 것 */
	List<MoveReminder> selectMoveReminders(int days);
}
