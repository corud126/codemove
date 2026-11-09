package com.codemove.web.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

import com.codemove.admin.LoginAdmin;
import com.codemove.domain.ReviewPhoto;
import com.codemove.review.AdminReviewView;
import com.codemove.review.ReviewService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 리뷰 관리 SCR-A-012.
 * 목록(평균 별점 · 분포 · 카드) · 답변 등록/수정 · 답변 삭제(답변 대기로). 별점 · 답변 필터는 review.js 가 화면에서 거른다.
 * 답변 저장 · 삭제는 review.js 가 fetch 로 보내고 {ok, repliedAt} 또는 {message} 를 받는다.
 */
@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

	private final ReviewService reviewService;

	@GetMapping
	public String list(Model model) {
		List<AdminReviewView> reviews = reviewService.adminList();
		model.addAttribute("reviews", reviews);
		model.addAttribute("stats", AdminReviewView.Stats.of(reviews));
		return "admin/review/list";
	}

	/** 답변 등록 · 수정: {content} */
	@PostMapping("/{id:\\d+}/reply")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
		LoginAdmin admin = (LoginAdmin) session.getAttribute(AdminAuthController.ADMIN_KEY);
		try {
			String repliedAt = reviewService.reply(id, admin.adminId(), body.get("content"));
			return ResponseEntity.ok(Map.of("ok", true, "repliedAt", repliedAt));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	/** 답변 삭제 → 답변 대기 */
	@PostMapping("/{id:\\d+}/reply/delete")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> deleteReply(@PathVariable Long id) {
		try {
			reviewService.deleteReply(id);
			return ResponseEntity.ok(Map.of("ok", true));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	/** 후기 사진 */
	@GetMapping("/photos/{id:\\d+}")
	public ResponseEntity<Resource> photo(@PathVariable Long id) {
		ReviewPhoto photo = reviewService.photo(id);
		if (photo == null || !Files.exists(Path.of(photo.getFilePath()))) {
			return ResponseEntity.notFound().build();
		}
		MediaType type = MediaTypeFactory.getMediaType(photo.getFilePath()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		return ResponseEntity.ok().contentType(type).body(new FileSystemResource(photo.getFilePath()));
	}
}
