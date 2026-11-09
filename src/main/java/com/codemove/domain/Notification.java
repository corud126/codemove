package com.codemove.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 알림 (TBL_NOTIFICATION) */
@Getter
@Setter
public class Notification {

	private Long notificationId;
	/** RECEIVER_TYPE: 01 사용자(회원) 수신 · 02 관리자 수신 */
	private String receiverType;
	/** 회원 수신이면 받는 회원, 관리자 수신이면 관련 회원 (없으면 NULL) */
	private Long memberId;
	/** NTF_TYPE: 01 내 이사 · 03 관리자 업무. 세부 종류에서 자동으로 정한다 (NotificationDetail) */
	private String ntfType;
	/** NTF_DETAIL: 세부 종류 (NotificationDetail) */
	private String ntfDetail;
	private String ntfTitle;
	private String ntfContent;
	/** 알림을 누르면 이동할 화면 (상대 경로) */
	private String linkUrl;
	private String isImportant;
	private String isRead;
	/** NTF_CHANNEL: 01 앱 · 02 알림톡 · 03 문자(알림톡 실패 시 대체) */
	private String ntfChannel;
	private LocalDateTime createdAt;
	/** 실제 발송 시각. 야간 발송 보류 중이면 NULL */
	private LocalDateTime sentAt;

	/** 조회용 (TBL_MEMBER 조인): 관리자 발송 내역의 고객 이름 · 휴대폰 */
	private String memberName;
	private String memberPhone;
}
