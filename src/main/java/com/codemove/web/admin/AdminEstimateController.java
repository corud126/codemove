package com.codemove.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 견적 관리 SCR-A-001 ~ A-004.
 * 화면 개발 단계: 더미 데이터는 템플릿에 있음. 상태는 ?state= 로 확인.
 */
@Controller
@RequestMapping("/admin/estimates")
public class AdminEstimateController {

	/** SCR-A-001 견적 관리 */
	@GetMapping
	public String list() {
		return "admin/estimate/list";
	}

	/** SCR-A-003/004 견적서 등록 */
	@GetMapping("/new")
	public String register() {
		return "admin/estimate/register";
	}

	/** SCR-A-002 견적 수정 */
	@GetMapping("/{id:\\d+}")
	public String edit(@PathVariable Long id) {
		return "admin/estimate/edit";
	}
}
