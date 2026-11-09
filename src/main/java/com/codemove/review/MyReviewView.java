package com.codemove.review;

import java.util.List;

/**
 * 더보기 &gt; 후기 '내가 쓴 후기' 한 건 (G-H09). 값은 화면 글자 그대로.
 * stars: ★★★★☆ · date: 2025. 11. 03 (방금 쓴 후기는 '방금 전') · route: 송파구 → 성동구 · type: 원룸 · 반포장
 * photos: 사진 주소 (SORT_ORDER 순) · reply/replyDate: 사장님 답변, 없으면 null
 * SAMPLES: 미리보기(/dev)용 예시
 */
public record MyReviewView(Long id, String stars, String date, String route, String content, String type, List<String> photos,
		String reply, String replyDate) {

	public static final List<MyReviewView> SAMPLES = List.of(new MyReviewView(1L, "★★★★★", "2025. 11. 03", "송파구 → 성동구",
			"친절하게 응대해주시고 꼼꼼히 챙겨주셔서 편하게 이사했습니다.", "원룸 · 반포장", List.of(),
			"소중한 후기 감사합니다. 앞으로도 안전하고 꼼꼼한 이사 서비스로 보답하겠습니다.", "2025. 11. 04"));
}
