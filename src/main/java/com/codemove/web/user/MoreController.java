package com.codemove.web.user;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.domain.Member;
import com.codemove.domain.ReviewPhoto;
import com.codemove.domain.Social;
import com.codemove.member.LoginMember;
import com.codemove.member.MemberService;
import com.codemove.reservation.HistoryService;
import com.codemove.review.ReviewService;
import com.codemove.review.ReviewTargetView;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 더보기 화면 그룹 (G-H01~H14): 더보기, 내 정보 수정, 견적내역, 예약내역, 후기, 후기 작성, 알림.
 * 내 정보 수정·탈퇴, 견적내역·예약내역, 후기는 DB 연동. 화면에 ?state= 가 있으면 캡처 상태 미리보기.
 */
@Controller
@RequiredArgsConstructor
public class MoreController {

	private final MemberService memberService;
	private final ReviewService reviewService;
	private final HistoryService historyService;

	@GetMapping("/more")
	public String more() {
		return "user/more/index";
	}

	/** 내 정보 수정: 이름 · 번호 · 연결된 간편 로그인 · 가입일 (휴대폰 번호 변경은 아직 more.js 시뮬레이션) */
	@GetMapping("/more/profile")
	public String profile(HttpSession session, Model model) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		Member member = memberService.findActiveById(login.memberId());
		if (member == null) {
			session.invalidate();
			return "redirect:/onboarding";
		}
		model.addAttribute("member", member);
		model.addAttribute("loginTypes", memberService.socials(member.getMemberId()).stream()
				.map(Social::getLoginType).toList());
		return "user/more/profile";
	}

	/** 회원 탈퇴: 이름·번호 파기 후 로그아웃 (MemberService.withdraw) */
	@PostMapping("/more/withdraw")
	public String withdraw(HttpSession session) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		memberService.withdraw(login.memberId());
		session.invalidate();
		return "redirect:/onboarding";
	}

	/** 견적내역: 견적 도착 카드 + 전체 견적 (HistoryService) */
	@GetMapping("/more/estimates")
	public String estimates(HttpSession session, Model model) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		model.addAttribute("estimates", historyService.estimates(login.memberId()));
		return "user/more/estimates";
	}

	/** 예약내역: 다가오는 예약 확정 + 지난 이사(이사 완료 · 취소) 연도별 (HistoryService) */
	@GetMapping("/more/reservations")
	public String reservations(HttpSession session, Model model) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		model.addAttribute("reservations", historyService.reservations(login.memberId()));
		return "user/more/reservations";
	}

	/** 후기 (G-H09 · H12): 내가 쓴 후기 + 사장님 답변, 위에 아직 후기를 안 쓴 최근 이사 완료 예약. ?state=done 등록 완료 토스트 */
	@GetMapping("/more/reviews")
	public String reviews(HttpSession session, Model model) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		model.addAttribute("reviews", reviewService.myReviews(login.memberId()));
		model.addAttribute("reviewTarget", reviewService.target(login.memberId(), null));
		return "user/more/reviews";
	}

	/** 후기 작성 (G-H10 · H11): ?reservation= 본인 · 이사 완료 · 후기 없는 예약만 (없으면 가장 최근 이사 완료 예약) */
	@GetMapping("/more/reviews/new")
	public String reviewNew(@RequestParam(required = false) Long reservation, HttpSession session, Model model) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		ReviewTargetView target = reviewService.target(login.memberId(), reservation);
		if (target == null) {
			return "redirect:/more/reviews";
		}
		model.addAttribute("reviewTarget", target);
		return "user/more/review-new";
	}

	/** 후기 등록 (more.js 가 multipart 로 보냄) → {redirect} 또는 {message} */
	@PostMapping("/more/reviews")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> writeReview(@RequestParam(required = false) Long reservation,
			@RequestParam(required = false) Integer rating, @RequestParam(defaultValue = "") String content,
			@RequestParam(required = false) List<MultipartFile> photos, HttpSession session) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		try {
			reviewService.write(login.memberId(), reservation, rating, content, photos);
			return ResponseEntity.ok(Map.of("redirect", "/more/reviews?state=done"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}

	/** 내 후기 사진 */
	@GetMapping("/more/reviews/photos/{id}")
	public ResponseEntity<Resource> reviewPhoto(@PathVariable Long id, HttpSession session) {
		LoginMember login = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		ReviewPhoto photo = reviewService.memberPhoto(login.memberId(), id);
		if (photo == null || !Files.exists(Path.of(photo.getFilePath()))) {
			return ResponseEntity.notFound().build();
		}
		MediaType type = MediaTypeFactory.getMediaType(photo.getFilePath()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		return ResponseEntity.ok().contentType(type).body(new FileSystemResource(photo.getFilePath()));
	}

	@GetMapping("/notifications")
	public String notifications() {
		return "user/more/notifications";
	}
}
