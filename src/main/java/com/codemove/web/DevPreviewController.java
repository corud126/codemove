package com.codemove.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 화면 개발용 템플릿 미리보기 (local 프로필 전용).
 * /dev/user/quote → templates/user/quote.html 을 컨트롤러 없이 렌더링한다.
 * 화면 상태는 템플릿에서 ${param.state} 로 분기한다.
 */
@Profile("local")
@Controller
public class DevPreviewController {

	@GetMapping("/dev/{*path}")
	public String preview(@PathVariable String path) {
		return path.substring(1).replace("..", "");
	}
}
