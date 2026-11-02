package com.codemove.web.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 이사 견적 (SCR-U-003~010).
 * 화면 개발 단계: 6단계 진행은 quote.js 가 처리하고, ?step=1~6&state= 로 캡처 상태를 바로 연다.
 */
@Controller
public class QuoteController {

	@GetMapping("/quote")
	public String quote() {
		return "user/quote/index";
	}

	@GetMapping("/quote/complete")
	public String complete() {
		return "user/quote/complete";
	}
}
