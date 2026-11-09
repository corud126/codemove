package com.codemove.web.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.codemove.admin.LoginAdmin;
import com.codemove.quote.QuoteService;
import com.codemove.quote.SheetForm;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 견적 관리 SCR-A-001 ~ A-004.
 * 목록 · 견적 확인(견적 정보 · 고객 영상 · 견적서) · 견적서 저장 · 승인 · 삭제. 견적서 등록(사진·PDF)은 아직 더미 화면.
 * 저장·승인·삭제는 estimate.js 가 fetch 로 보내고 {ok} 또는 {message} 를 받는다.
 */
@Controller
@RequestMapping("/admin/estimates")
@RequiredArgsConstructor
public class AdminEstimateController {

	private final QuoteService quoteService;

	/** SCR-A-001 견적 관리 — 행은 템플릿 형식 'id|분류|이름|YY.MM.DD|삭제 가능(Y/N)' (분류 필터·검색·페이지는 estimate.js) */
	@GetMapping
	public String list(Model model) {
		model.addAttribute("rows", quoteService.list().stream()
				.map(r -> r.id() + "|" + r.status() + "|" + r.name().replace("|", " ") + "|" + r.date() + "|" + (r.deletable() ? "Y" : "N"))
				.toList());
		return "admin/estimate/list";
	}

	/** SCR-A-003/004 견적서 등록 */
	@GetMapping("/new")
	public String register() {
		return "admin/estimate/register";
	}

	/** SCR-A-002 견적 확인 */
	@GetMapping("/{id:\\d+}")
	public String edit(@PathVariable Long id, Model model) {
		QuoteService.Detail detail = quoteService.detail(id);
		if (detail == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		model.addAttribute("detail", quoteService.detailView(detail));
		model.addAttribute("sheetView", quoteService.sheetView(detail));
		return "admin/estimate/edit";
	}

	/** 고객 영상 (브라우저 video 태그가 구간 요청으로 이어 받는다) */
	@GetMapping("/{id:\\d+}/video")
	public ResponseEntity<Resource> video(@PathVariable Long id) {
		QuoteService.Detail detail = quoteService.detail(id);
		if (detail == null || detail.video() == null || !Files.exists(Path.of(detail.video().getFilePath()))) {
			return ResponseEntity.notFound().build();
		}
		Resource file = new FileSystemResource(detail.video().getFilePath());
		MediaType type = MediaTypeFactory.getMediaType(detail.video().getFileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		return ResponseEntity.ok().contentType(type).body(file);
	}

	@PostMapping("/{id:\\d+}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> save(@PathVariable Long id, @RequestBody SheetForm form) {
		return run(() -> quoteService.save(id, form));
	}

	@PostMapping("/{id:\\d+}/approve")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, HttpSession session) {
		LoginAdmin admin = (LoginAdmin) session.getAttribute(AdminAuthController.ADMIN_KEY);
		return run(() -> quoteService.approve(id, admin.adminId()));
	}

	@PostMapping("/{id:\\d+}/delete")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
		return run(() -> quoteService.delete(id));
	}

	/** 서비스가 던진 안내 문구를 화면에 그대로 돌려준다 */
	private static ResponseEntity<Map<String, Object>> run(Runnable action) {
		try {
			action.run();
			return ResponseEntity.ok(Map.of("ok", true));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}
}
