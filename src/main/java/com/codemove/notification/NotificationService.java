package com.codemove.notification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.codemove.common.code.CodeService;
import com.codemove.common.sms.SmsSender;
import com.codemove.domain.Notification;
import com.codemove.domain.NotificationSetting;

import lombok.extern.slf4j.Slf4j;

/**
 * 알림 (TBL_NOTIFICATION). 다른 기능은 아래 두 메서드만 부르면 된다.
 * <ul>
 * <li>{@link #notifyMember} — 회원에게 (사용자 알림 화면 · 헤더 종 · 알림톡/문자)</li>
 * <li>{@link #notifyAdmins} — 관리자 전체에게 (관리자 알림 패널 · 알림 받기를 켠 관리자 휴대폰 푸시)</li>
 * </ul>
 * 규칙 (ERD Q8 · Q9 · Q22, admin-screen-mapping SCR-A-013~015 · A-019)
 * <ul>
 * <li>세부 종류(NTF_DETAIL)만 넘기면 분류(NTF_TYPE) · 중요 표시는 {@link NotificationDetail} 에서 정한다</li>
 * <li>호출한 쪽의 트랜잭션에 함께 들어간다 (업무가 롤백되면 알림 행도 함께 없어짐). 휴대폰 발송은 커밋된 뒤에 한다</li>
 * <li>알림 저장이 DB 오류로 실패해도 호출한 업무는 계속된다 (로그만 남김)</li>
 * <li>앱 안 알림은 항상 바로 저장. 고객 알림톡·문자만 알림 설정(자동 발송 · 야간 보류 · 문자 대체)을 따른다.
 * 야간 보류 중이면 SENT_AT NULL 로 두고 보류가 끝나면 {@link #flushHeld()} 가 보낸다</li>
 * <li>관리자 알림은 관리자 공용(목록·읽음 모두 공용). 알림 받기(PUSH_ENABLED)를 끈 관리자는 푸시만 받지 않는다</li>
 * </ul>
 */
@Slf4j
@Service
public class NotificationService {

	public static final String TO_MEMBER = "01";
	public static final String TO_ADMIN = "02";
	public static final String CH_APP = "01";
	public static final String CH_ALIMTALK = "02";
	public static final String CH_SMS = "03";

	private static final int TITLE_MAX = 100;
	private static final int CONTENT_MAX = 500;
	private static final int LINK_MAX = 300;
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yy.MM.dd");
	private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");

	private final NotificationMapper mapper;
	private final NotificationSettingService settingService;
	private final CodeService codeService;
	private final MessageSender messageSender;
	private final SmsSender smsSender;
	private final TransactionTemplate newTx;

	private final int recentDays;
	private final int pageSize;
	private final int userListSize;
	private final int panelSize;
	private final int reminderDays;

	public NotificationService(NotificationMapper mapper, NotificationSettingService settingService, CodeService codeService,
			MessageSender messageSender, SmsSender smsSender, PlatformTransactionManager txManager,
			@Value("${codemove.notification.recent-days:30}") int recentDays,
			@Value("${codemove.notification.page-size:20}") int pageSize,
			@Value("${codemove.notification.user-list-size:100}") int userListSize,
			@Value("${codemove.notification.panel-size:100}") int panelSize,
			@Value("${codemove.notification.reminder-days:3}") int reminderDays) {
		this.mapper = mapper;
		this.settingService = settingService;
		this.codeService = codeService;
		this.messageSender = messageSender;
		this.smsSender = smsSender;
		this.newTx = new TransactionTemplate(txManager);
		this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.recentDays = recentDays;
		this.pageSize = pageSize;
		this.userListSize = userListSize;
		this.panelSize = panelSize;
		this.reminderDays = reminderDays;
	}

	// ==================== 알림 보내기 (다른 기능에서 호출) ====================

	/**
	 * 회원에게 알림. 사용자 알림 화면(내 이사 탭)과 헤더 종에 바로 보이고, 알림 설정에 따라 알림톡(실패 시 문자)으로도 보낸다.
	 *
	 * @param memberId   받는 회원
	 * @param detailCode NTF_DETAIL — 회원용 세부 종류 ({@link NotificationDetail#forMember()}: 01~10, 15). 예: {@code NotificationDetail.QUOTE_ARRIVED.code()}
	 * @param title      제목 (100자까지, 넘으면 자름). 예: "견적서가 도착했어요"
	 * @param content    내용 (500자까지, 넘으면 자름)
	 * @param link       누르면 이동할 사용자 화면 상대 경로 (예: "/estimates/12"), 없으면 null
	 * @return 만든 알림 ID (저장 실패 시 null)
	 * @throws IllegalArgumentException 회원이 없거나, 회원용이 아닌 세부 종류이거나, 제목·내용이 비었을 때 (개발 오류)
	 */
	public Long notifyMember(Long memberId, String detailCode, String title, String content, String link) {
		NotificationDetail detail = NotificationDetail.of(detailCode);
		if (memberId == null || !detail.forMember()) {
			throw new IllegalArgumentException("회원에게 보낼 수 없는 알림입니다: " + detailCode);
		}
		Notification n = build(TO_MEMBER, memberId, detail, title, content, link);
		try {
			NotificationSetting setting = settingService.get();
			String phone = mapper.selectMemberPhone(memberId);
			boolean phoneSend = setting.isActiveOn() && phone != null;
			n.setNtfChannel(phoneSend ? CH_ALIMTALK : CH_APP);
			boolean held = phoneSend && settingService.isQuietNow(setting, LocalTime.now());
			n.setSentAt(held ? null : LocalDateTime.now());
			mapper.insert(n);
			if (phoneSend && !held) {
				boolean fallback = setting.isSmsFallbackOn();
				afterCommit(() -> sendAlimtalk(n, phone, fallback));
			}
			return n.getNotificationId();
		} catch (RuntimeException e) {
			log.warn("회원 알림 저장 실패 memberId={} detail={}", memberId, detailCode, e);
			return null;
		}
	}

	/**
	 * 관리자 전체에게 알림. 관리자 알림 패널(공용)에 쌓이고, 알림 받기를 켠 관리자 휴대폰으로 푸시한다.
	 *
	 * @param relatedMemberId 관련 회원 (예: 상담을 요청한 고객). 시스템 알림이면 null
	 * @param detailCode      NTF_DETAIL — 예: 01 견적 접수 · 04 예약 확정 · 05 예약 취소 · 08 오늘의 일정 · 13 채팅 대기 · 14 신규 가입
	 * @param title           푸시 제목 (100자까지). 패널에는 세부 종류 이름이 제목처럼 보인다
	 * @param content         패널·푸시 내용 (500자까지). 예: "홍길동 고객님의 견적 요청이 접수되었습니다."
	 * @param link            누르면 이동할 관리자 화면 상대 경로 (예: "/admin/estimates/12"), 없으면 null
	 * @return 만든 알림 ID (저장 실패 시 null)
	 * @throws IllegalArgumentException 없는 세부 종류이거나 제목·내용이 비었을 때 (개발 오류)
	 */
	public Long notifyAdmins(Long relatedMemberId, String detailCode, String title, String content, String link) {
		NotificationDetail detail = NotificationDetail.of(detailCode);
		Notification n = build(TO_ADMIN, relatedMemberId, detail, title, content, link);
		n.setNtfChannel(CH_APP);
		n.setSentAt(LocalDateTime.now());
		try {
			mapper.insert(n);
			List<String> phones = mapper.selectPushPhones();
			afterCommit(() -> phones.forEach(p -> messageSender.sendPush(p, n.getNtfTitle(), n.getNtfContent())));
			return n.getNotificationId();
		} catch (RuntimeException e) {
			log.warn("관리자 알림 저장 실패 detail={}", detailCode, e);
			return null;
		}
	}

	private Notification build(String receiver, Long memberId, NotificationDetail detail, String title, String content, String link) {
		if (title == null || title.isBlank() || content == null || content.isBlank()) {
			throw new IllegalArgumentException("알림 제목과 내용이 필요합니다.");
		}
		Notification n = new Notification();
		n.setReceiverType(receiver);
		n.setMemberId(memberId);
		n.setNtfType(detail.type());
		n.setNtfDetail(detail.code());
		n.setNtfTitle(cut(title.strip(), TITLE_MAX));
		n.setNtfContent(cut(content.strip(), CONTENT_MAX));
		n.setLinkUrl(link == null || link.isBlank() || link.length() > LINK_MAX ? null : link.strip());
		n.setIsImportant(detail.important() ? "Y" : "N");
		return n;
	}

	// ==================== 휴대폰 발송 ====================

	/** 커밋된 뒤에 실행 (트랜잭션이 없으면 바로). 발송 오류는 로그만 */
	private void afterCommit(Runnable task) {
		Runnable safe = () -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				log.warn("알림 발송 실패", e);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					safe.run();
				}
			});
		} else {
			safe.run();
		}
	}

	/** 알림톡 → 실패하면 (설정이 켜져 있으면) 문자로 대체하고 실제 발송 경로(03)를 기록 */
	private void sendAlimtalk(Notification n, String phone, boolean smsFallback) {
		if (messageSender.sendAlimtalk(phone, n.getNtfTitle(), n.getNtfContent())) {
			return;
		}
		if (smsFallback) {
			smsSender.send(phone, "[명품익스프레스] " + n.getNtfTitle() + "\n" + n.getNtfContent());
			newTx.executeWithoutResult(s -> mapper.updateChannel(n.getNotificationId(), CH_SMS));
		}
	}

	/**
	 * 야간 보류된 고객 알림 일괄 발송 (NotificationScheduler, 보류 시간이 아닐 때만).
	 * 그 사이 자동 발송을 껐거나 번호가 없어졌으면 앱 알림(01)으로만 처리한다.
	 *
	 * @return 처리한 건수
	 */
	@Transactional
	public int flushHeld() {
		NotificationSetting setting = settingService.get();
		if (settingService.isQuietNow(setting, LocalTime.now())) {
			return 0;
		}
		int count = 0;
		for (Notification n : mapper.selectHeld()) {
			String phone = mapper.selectMemberPhone(n.getMemberId());
			boolean phoneSend = setting.isActiveOn() && phone != null;
			if (mapper.markSent(n.getNotificationId(), phoneSend ? CH_ALIMTALK : CH_APP) == 0) {
				continue;
			}
			count++;
			if (phoneSend) {
				boolean fallback = setting.isSmsFallbackOn();
				afterCommit(() -> sendAlimtalk(n, phone, fallback));
			}
		}
		return count;
	}

	/**
	 * 이사 임박 알림 (NTF_DETAIL 07): 이사일이 오늘 + N일(기본 3)인 예약 확정 건의 회원에게 한 번씩.
	 * 같은 예약에 이미 보냈으면 다시 보내지 않는다 (LINK_URL 로 확인). NotificationScheduler 가 매일 실행.
	 *
	 * @return 보낸 건수
	 */
	@Transactional
	public int sendMoveReminders() {
		int count = 0;
		for (MoveReminder r : mapper.selectMoveReminders(reminderDays)) {
			String when = moveDateText(r.getMoveDate()) + (r.getMoveTime() != null ? " " + timeText(r.getMoveTime()) : "");
			Long id = notifyMember(r.getMemberId(), NotificationDetail.MOVE_D3.code(), "이사까지 " + reminderDays + "일 남았어요",
					when + " 이사 예정이에요. 짐 정리와 준비 사항을 확인해 주세요.", "/reservations/" + r.getReservationId());
			if (id != null) {
				count++;
			}
		}
		return count;
	}

	// ==================== 회원 화면 ====================

	/** 사용자 알림 화면 목록 (최근 순) */
	public List<NotificationViews.UserItem> memberItems(Long memberId) {
		LocalDateTime now = LocalDateTime.now();
		return mapper.selectForMember(memberId, userListSize).stream().map(n -> {
			NotificationDetail d = NotificationDetail.find(n.getNtfDetail());
			return new NotificationViews.UserItem(n.getNotificationId(), n.getNtfTitle(), n.getNtfContent(),
					ago(n.getCreatedAt(), now), d != null ? d.userIcon() : "default",
					NotificationDetail.MY_MOVE.equals(n.getNtfType()) ? "move" : "etc", n.getLinkUrl(), "N".equals(n.getIsRead()));
		}).toList();
	}

	/** 헤더 종의 안 읽은 알림 수 */
	public int unreadCount(Long memberId) {
		return mapper.countUnreadForMember(memberId);
	}

	/** 회원 본인 알림 읽음. 본인 알림이 아니면 false */
	@Transactional
	public boolean markRead(Long memberId, Long notificationId) {
		return mapper.markReadForMember(notificationId, memberId) > 0;
	}

	@Transactional
	public int markAllRead(Long memberId) {
		return mapper.markAllReadForMember(memberId);
	}

	// ==================== 관리자 알림 패널 ====================

	/** 최근 30일 관리자 수신 알림 → 오늘 · 어제 · 이전 */
	public NotificationViews.AdminPanel adminPanel() {
		LocalDateTime now = LocalDateTime.now();
		LocalDate today = now.toLocalDate();
		Map<String, List<NotificationViews.PanelItem>> groups = new LinkedHashMap<>();
		groups.put("오늘", new ArrayList<>());
		groups.put("어제", new ArrayList<>());
		groups.put("이전", new ArrayList<>());
		for (Notification n : mapper.selectAdminPanel(recentDays, panelSize)) {
			LocalDate day = n.getCreatedAt().toLocalDate();
			String group = day.equals(today) ? "오늘" : day.equals(today.minusDays(1)) ? "어제" : "이전";
			String time = switch (group) {
			case "오늘" -> ago(n.getCreatedAt(), now);
			case "어제" -> timeText(n.getCreatedAt().format(HM));
			default -> n.getCreatedAt().format(MD);
			};
			NotificationDetail d = NotificationDetail.find(n.getNtfDetail());
			String name = codeService.name("NTF_DETAIL", n.getNtfDetail());
			groups.get(group).add(new NotificationViews.PanelItem(n.getNotificationId(), name != null ? name : n.getNtfTitle(),
					n.getNtfContent(), time, d != null ? d.adminIcon() : "bell", n.getLinkUrl(), "Y".equals(n.getIsImportant()),
					"N".equals(n.getIsRead())));
		}
		List<NotificationViews.PanelGroup> list = groups.entrySet().stream()
				.map(e -> new NotificationViews.PanelGroup(e.getKey(), e.getValue())).toList();
		return new NotificationViews.AdminPanel(list, mapper.countAdminUnread(recentDays));
	}

	@Transactional
	public boolean markAdminRead(Long notificationId) {
		return mapper.markAdminRead(notificationId) > 0;
	}

	@Transactional
	public int markAllAdminRead() {
		return mapper.markAllAdminRead();
	}

	// ==================== 관리자 발송 내역 ====================

	/**
	 * 발송 내역 한 페이지 (고객 수신 알림만). 기본 최근 30일, 날짜 검색이면 범위 제한 없음.
	 *
	 * @param query   이름 또는 날짜 (HistoryQuery 규칙)
	 * @param channel 채널 이름(앱 · 알림톡 · 문자) 또는 코드, 없으면 전체
	 * @param result  held · sms · sent, 없으면 전체
	 * @param cursor  이전 페이지의 nextCursor (첫 페이지는 null)
	 */
	public NotificationViews.History history(String query, String channel, String result, Long cursor) {
		String ch = channel == null || channel.isBlank() ? null
				: channel.matches("\\d{2}") ? channel : codeService.codeOf("NTF_CHANNEL", channel.strip());
		if (channel != null && !channel.isBlank() && ch == null) {
			ch = "--";
		}
		HistoryQuery q = HistoryQuery.of(query, ch, result, recentDays);
		List<Notification> found = mapper.selectHistory(q, cursor, pageSize + 1);
		boolean more = found.size() > pageSize;
		List<Notification> page = more ? found.subList(0, pageSize) : found;
		LocalDate today = LocalDate.now();
		List<NotificationViews.HistoryRow> rows = page.stream().map(n -> historyRow(n, today)).toList();
		return new NotificationViews.History(mapper.countSentToday(), mapper.countHeld(), mapper.countSmsFallback(recentDays),
				mapper.countHistory(q), rows, more ? page.get(page.size() - 1).getNotificationId() : null);
	}

	private NotificationViews.HistoryRow historyRow(Notification n, LocalDate today) {
		String result = n.getSentAt() == null ? "held" : CH_SMS.equals(n.getNtfChannel()) ? "sms" : "sent";
		LocalDate day = n.getCreatedAt().toLocalDate();
		String group = day.equals(today) ? "today" : day.equals(today.minusDays(1)) ? "yesterday" : "older";
		String phone = n.getMemberPhone() == null ? "" : n.getMemberPhone().replaceAll("\\D", "");
		String detail = codeService.userName("NTF_DETAIL", n.getNtfDetail());
		String channel = codeService.name("NTF_CHANNEL", n.getNtfChannel());
		return new NotificationViews.HistoryRow(n.getNotificationId(), n.getMemberName() != null ? n.getMemberName() : "-",
				phone.length() >= 4 ? phone.substring(phone.length() - 4) : "", detail != null ? detail : "",
				n.getNtfTitle(), n.getNtfContent(), channel != null ? channel : "", result, n.getCreatedAt().format(YMD),
				n.getCreatedAt().format(HM), group,
				n.getSentAt() == null ? "" : n.getSentAt().format(YMD) + " " + n.getSentAt().format(HM));
	}

	// ==================== 표시 형식 ====================

	/** "방금 전" · "10분 전" · "14시간 전" · "2일 전" · 30일 넘으면 "26.10.01" */
	static String ago(LocalDateTime at, LocalDateTime now) {
		Duration d = Duration.between(at, now);
		if (d.toMinutes() < 1) {
			return "방금 전";
		}
		if (d.toMinutes() < 60) {
			return d.toMinutes() + "분 전";
		}
		if (d.toHours() < 24) {
			return d.toHours() + "시간 전";
		}
		if (d.toDays() <= 30) {
			return d.toDays() + "일 전";
		}
		return at.format(YMD);
	}

	/** "13:00" → "오후 1:00" */
	static String timeText(String hhmm) {
		try {
			LocalTime t = LocalTime.parse(hhmm);
			int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
			return (t.getHour() < 12 ? "오전 " : "오후 ") + h + ":" + String.format("%02d", t.getMinute());
		} catch (RuntimeException e) {
			return hhmm;
		}
	}

	/** "11월 12일 (목)" */
	static String moveDateText(LocalDate date) {
		return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 ("
				+ date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}
}
