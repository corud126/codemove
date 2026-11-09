package com.codemove.review;

/**
 * 홈 '실제 고객 후기' 카드 (HomeController.ReviewCard 와 같은 모양).
 * name: 김*드 (가운데 가림) · meta: 이용 2회차 · 어제 · type: 원룸(반포장) · stars: ★★★★☆
 */
public record HomeReview(String name, String meta, String type, String stars, String content) {
}
