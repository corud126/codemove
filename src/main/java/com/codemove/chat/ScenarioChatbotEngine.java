package com.codemove.chat;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.codemove.domain.ChatbotTopic;
import com.codemove.domain.Lorebook;
import com.codemove.domain.QuickMenu;

import lombok.RequiredArgsConstructor;

/**
 * DB 시나리오 챗봇 (외부 AI 키 없이 동작).
 * - 주제를 고르고 온 질문(퀵메뉴 · 주제 버튼): 그 주제로 답한다
 * - 직접 입력: 사용 중인 주제의 로어북 키워드 · 주제 제목 낱말이 질문에 몇 개 들어 있는지로 주제를 고른다
 * - 안내형(01): 챗봇이 처음 하는 말(INTRO) 또는 가장 잘 맞는 로어북 내용
 * - 예약 조회(02) · 견적 조회(03): 회원의 예약 · 견적 데이터로 문장을 만든다
 * - 못 찾으면 퀵메뉴 · 상담사 연결 버튼을 붙인 기본 안내
 */
@Component
@RequiredArgsConstructor
public class ScenarioChatbotEngine implements ChatbotEngine {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
	private static final Pattern DISTRICT = Pattern.compile("(\\S+(?:구|군))(?:\\s|$)");

	private final ChatbotMapper chatbotMapper;
	private final ChatMapper chatMapper;

	@Override
	public Reply answer(Question q) {
		List<ChatbotTopic> topics = activeTopics();
		if (q.topicId() != null) {
			ChatbotTopic topic = topics.stream().filter(t -> t.getTopicId().equals(q.topicId())).findFirst().orElse(null);
			if (topic != null) {
				return answerTopic(topic, null, q);
			}
		}
		String text = normalize(q.text());
		ChatbotTopic best = null;
		Lorebook bestEntry = null;
		int bestScore = 0;
		for (ChatbotTopic topic : topics) {
			int titleScore = (int) words(topic.getTitle()).stream().filter(text::contains).count();
			Lorebook entry = null;
			int entryScore = 0;
			for (Lorebook lb : topic.getLorebooks()) {
				int s = (int) keywords(lb.getKeywords()).stream().filter(text::contains).count();
				if (s > entryScore) {
					entry = lb;
					entryScore = s;
				}
			}
			int score = entryScore * 2 + titleScore;
			if (score > bestScore) {
				best = topic;
				bestEntry = entry;
				bestScore = score;
			}
		}
		return best != null ? answerTopic(best, bestEntry, q) : fallback();
	}

	private Reply answerTopic(ChatbotTopic topic, Lorebook entry, Question q) {
		String text = switch (topic.getTopicType()) {
			case ChatbotTopic.RESERVATION -> reservationText(q);
			case ChatbotTopic.QUOTE -> quoteText(q);
			default -> guideText(topic, entry);
		};
		return new Reply(text, topic.getTopicId(), List.of());
	}

	/** 주제를 골라서 왔으면 INTRO, 키워드로 찾았으면 그 로어북 내용 */
	private static String guideText(ChatbotTopic topic, Lorebook entry) {
		if (entry != null) {
			return entry.getContent();
		}
		if (topic.getIntro() != null && !topic.getIntro().isBlank()) {
			return topic.getIntro();
		}
		if (!topic.getLorebooks().isEmpty()) {
			return topic.getLorebooks().get(0).getContent();
		}
		return topic.getTitle() + "에 대해 안내해 드릴게요.\n더 궁금한 점은 메시지로 남기거나 상담사에게 문의해 주세요.";
	}

	private String reservationText(Question q) {
		MemberReservation r = chatMapper.selectMemberReservation(q.memberId());
		if (r == null) {
			return "아직 확정된 이사 예약이 없어요.\n견적을 확인하고 확정하면 예약 정보를 여기서 바로 알려드릴게요.";
		}
		String name = q.memberName() != null ? q.memberName() + "님의 " : "";
		String when = r.getMoveDate().format(DATE) + (r.getMoveTime() != null ? " " + timeText(r.getMoveTime()) : "");
		String place = "출발지는 " + district(r.getFromAddress()) + ", 도착지는 " + district(r.getToAddress()) + "입니다.";
		return switch (r.getStatus()) {
			case "02" -> name + "이사는 " + when + "에 완료되었어요.\n이용해 주셔서 감사합니다.";
			case "03" -> name + when + " 이사 예약은 취소되었어요.\n다시 예약하시려면 견적을 새로 신청해 주세요.";
			default -> name + "이사 예약은 " + when + "로 확정되어 있어요.\n" + place;
		};
	}

	private String quoteText(Question q) {
		MemberQuote quote = chatMapper.selectMemberQuote(q.memberId());
		if (quote == null) {
			return "아직 받은 견적이 없어요.\n영상이나 간단한 정보로 이사 견적을 신청할 수 있어요.";
		}
		String amount = quote.getTotalAmount() != null
				? "\n현재 견적 금액은 " + NumberFormat.getInstance(Locale.KOREA).format(quote.getTotalAmount()) + "원이며, "
				: "\n";
		return switch (quote.getStatus()) {
			case "01" -> "담당자가 견적을 검토하고 있어요.\n견적이 준비되면 알림으로 알려드릴게요.";
			case "03" -> "확정된 견적이 있어요." + amount + "예약 정보는 내 이사에서 확인할 수 있어요.";
			case "04" -> "최근 견적은 유효 기간이 지나 만료되었어요.\n새로 견적을 신청하거나 상담사에게 문의해 주세요.";
			default -> "담당자가 확인한 견적이 준비되어 있어요." + amount
					+ "견적서의 작업 내용과 별도 비용을 확인한 뒤 확정할 수 있습니다.";
		};
	}

	/** 못 찾았을 때: 퀵메뉴 2개 + 상담사 연결 */
	private Reply fallback() {
		List<Button> buttons = new ArrayList<>();
		for (QuickMenu m : chatbotMapper.selectQuickMenus()) {
			if ("Y".equals(m.getIsActive()) && "Y".equals(m.getTopicActive()) && buttons.size() < 2) {
				buttons.add(new Button(m.getMenuName(), "topic", String.valueOf(m.getTopicId())));
			}
		}
		buttons.add(new Button("상담사 연결", "agent", null));
		return new Reply("문의 내용을 확인했어요.\n조금 더 자세히 알려주시거나 아래 버튼을 눌러 주세요.", null, buttons);
	}

	/** 사용 중인 주제 + 로어북 */
	private List<ChatbotTopic> activeTopics() {
		Map<Long, List<Lorebook>> lorebooks = chatbotMapper.selectLorebooks().stream()
				.collect(Collectors.groupingBy(Lorebook::getTopicId));
		List<ChatbotTopic> topics = new ArrayList<>();
		for (ChatbotTopic t : chatbotMapper.selectTopics()) {
			if (t.isActiveTopic()) {
				t.setLorebooks(lorebooks.getOrDefault(t.getTopicId(), List.of()));
				topics.add(t);
			}
		}
		return topics;
	}

	/** 비교용: 소문자 · 공백 제거 */
	static String normalize(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}

	/** "#견적#비용" → [견적, 비용] */
	static List<String> keywords(String raw) {
		return raw == null ? List.of()
				: Arrays.stream(raw.split("[#,]")).map(ScenarioChatbotEngine::normalize).filter(k -> !k.isEmpty()).toList();
	}

	/** 주제 제목의 두 글자 이상 낱말 ("내 예약 확인" → [예약, 확인]) */
	static List<String> words(String title) {
		return title == null ? List.of()
				: Arrays.stream(title.split("[\\s·,/]+")).map(ScenarioChatbotEngine::normalize).filter(w -> w.length() >= 2)
						.toList();
	}

	/** "10:00" → "오전 10시", "13:30" → "오후 1시 30분" */
	static String timeText(String hhmm) {
		try {
			int h = Integer.parseInt(hhmm.substring(0, 2));
			int m = Integer.parseInt(hhmm.substring(3, 5));
			return (h < 12 ? "오전 " : "오후 ") + (h % 12 == 0 ? 12 : h % 12) + "시" + (m > 0 ? " " + m + "분" : "");
		} catch (RuntimeException e) {
			return hhmm;
		}
	}

	/** 주소에서 구 · 군 이름만 ("서울 강남구 테헤란로" → 강남구), 없으면 주소 그대로 */
	static String district(String address) {
		if (address == null) {
			return "-";
		}
		Matcher m = DISTRICT.matcher(address);
		return m.find() ? m.group(1) : address;
	}
}
