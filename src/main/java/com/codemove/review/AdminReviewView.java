package com.codemove.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 관리자 리뷰 관리 카드 한 건 (SCR-A-012). 값은 화면 글자 그대로.
 * date: 작성일 26.06.02 · houseName/packName: 관리자 코드명(원룸이사 · 반포장) · from/to: 출발 구 · 도착 구
 * repliedDate: 답변일 26.06.03, 답변 대기면 '-' · reply: 답변 내용, 없으면 null · photos: SORT_ORDER 순
 * SAMPLES · SAMPLE_STATS: 미리보기(/dev)용 예시 (기존 더미 행 그대로)
 */
public record AdminReviewView(Long id, String name, String date, String houseName, String packName, String from, String to,
		int rating, String content, String repliedDate, String reply, List<Photo> photos) {

	/** 사진: 주소 · 썸네일 위치(object-position) */
	public record Photo(String url, String pos) {
	}

	/** 평균 별점 · 별점별 분포 (전체 기준) */
	public record Stats(String average, List<Bar> bars) {

		public static Stats of(List<AdminReviewView> reviews) {
			int[] counts = new int[6];
			reviews.forEach(r -> counts[r.rating()]++);
			int total = reviews.size();
			double sum = reviews.stream().mapToInt(AdminReviewView::rating).sum();
			List<Bar> bars = new ArrayList<>();
			for (int score = 5; score >= 1; score--) {
				bars.add(new Bar(score, counts[score], total == 0 ? 0 : (int) (counts[score] * 100L / total)));
			}
			return new Stats(String.format(Locale.ROOT, "%.1f", total == 0 ? 0 : sum / total), bars);
		}
	}

	/** 별점 한 줄: 점수 · 건수 · 비율(%) */
	public record Bar(int score, int count, int percent) {
	}

	public boolean answered() {
		return !"-".equals(repliedDate);
	}

	private static final String SAMPLE_REPLY = "소중한 후기 감사합니다. 앞으로도 안전하고 꼼꼼한 이사 서비스로 보답하겠습니다.";
	private static final String SAMPLE_PHOTO = "https://images.unsplash.com/photo-1730154838368-c37b1fdebcf6?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixlib=rb-4.1.0&q=80&w=500";

	public static final List<AdminReviewView> SAMPLES = List.of(
			sample("1|김명품|26.06.02|가정이사|포장|강남구|서초구|5|포장부터 정리까지 정말 꼼꼼하게 해주셨어요. 직원분들도 친절해서 안심하고 이사했습니다. 냉장고랑 세탁기 설치까지 깔끔하게 마무리해 주셨고, 바닥 보양도 꼼꼼히 해 주셔서 흠집 하나 없었어요. 다음 이사 때도 꼭 다시 이용하겠습니다.|26.06.03|3"),
			sample("2|이서준|26.05.29|기업이사|포장|마포구|중구|5|사무실 집기 이동이 많았는데 예정 시간 안에 잘 마무리해 주셨습니다.|-|0"),
			sample("3|박하윤|26.05.21|가정이사|보관|송파구|강동구|5|보관 상태도 좋았고 다시 배송받을 때 파손된 물건 없이 잘 도착했어요.|-|0"),
			sample("4|최유진|26.05.18|가정이사|반포장|용산구|은평구|4|전체적으로 괜찮았지만 도착 시간이 조금 늦어 아쉬웠습니다.|26.05.19|1"),
			sample("5|정도현|26.05.14|원룸이사|일반|관악구|동작구|5|짐이 많지 않았는데도 세심하게 포장해 주셔서 만족했습니다.|-|0"),
			sample("6|한지민|26.05.10|가정이사|포장|노원구|도봉구|5|상담부터 이사 완료까지 안내가 정확하고 친절했어요.|26.05.11|0"),
			sample("7|윤서아|26.05.07|원룸이사|보관|성북구|강북구|4|보관 일정 변경 과정에서 안내가 조금 늦었습니다.|-|0"),
			sample("8|강민준|26.05.03|기업이사|포장|영등포구|구로구|5|업무 공백 없이 빠르게 이전을 마칠 수 있어 좋았습니다.|26.05.04|0"),
			sample("9|송하윤|26.04.29|원룸이사|반포장|광진구|성동구|4|합리적인 가격에 서비스도 깔끔해서 다시 이용하고 싶어요.|-|0"),
			sample("10|임지호|26.04.24|가정이사|반포장|양천구|강서구|3|일부 가구에 흠집이 생겨서 사후 처리를 요청했습니다.|-|2"),
			sample("11|오지안|26.04.20|기업이사|포장|종로구|중구|5|복잡한 장비도 안전하게 옮겨 주셔서 정말 감사합니다.|26.04.21|0"),
			sample("12|문예준|26.04.16|가정이사|일반|서대문구|마포구|4|포장은 꼼꼼했지만 정리 시간이 예상보다 오래 걸렸습니다.|-|0"));

	public static final Stats SAMPLE_STATS = Stats.of(SAMPLES);

	/** id|이름|작성일|이사 유형|포장 방식|출발 구|도착 구|별점|내용|답변일(-)|사진 수 */
	private static AdminReviewView sample(String row) {
		String[] t = row.split("\\|");
		List<Photo> photos = new ArrayList<>();
		for (int p = 1; p <= Integer.parseInt(t[10]); p++) {
			photos.add(new Photo(SAMPLE_PHOTO, p == 1 ? "center" : p == 2 ? "left" : "right"));
		}
		return new AdminReviewView(Long.valueOf(t[0]), t[1], t[2], t[3], t[4], t[5], t[6], Integer.parseInt(t[7]), t[8], t[9],
				"-".equals(t[9]) ? null : SAMPLE_REPLY, List.copyOf(photos));
	}
}
