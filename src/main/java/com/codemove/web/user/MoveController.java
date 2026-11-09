package com.codemove.web.user;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.domain.Quote;
import com.codemove.domain.Reservation;
import com.codemove.member.LoginMember;
import com.codemove.quote.MyQuoteView;
import com.codemove.quote.QuoteService;
import com.codemove.reservation.ReservationMapper;
import com.codemove.reservation.ReservationService;
import com.codemove.reservation.ReservationView;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 내 이사 · 견적 상세 · 예약 상세 (G-D, G-E, G-F).
 * 견적 정보 · 예약 정보 · 완료 탭, 견적 상세(확정), 예약 상세(요청사항 · 취소)는 DB 연동.
 * ?state= 로 캡처 상태를 바로 여는 미리보기는 그대로 동작한다 (state 가 있으면 화면 값보다 우선).
 */
@Controller
@RequiredArgsConstructor
public class MoveController {

	private final QuoteService quoteService;
	private final ReservationService reservationService;
	private final ReservationMapper reservationMapper;

	/** 내 이사: ?tab=booking|estimate|done, ?state=... */
	@GetMapping("/move")
	public String move(HttpSession session, Model model) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);

		// 예약 정보: 다가오는 예약 확정(01) 중 가장 빠른 것 / 완료: 가장 최근 이사 완료(02)
		List<Reservation> reservations = reservationService.memberReservations(member.memberId());
		Reservation upcoming = reservations.stream().filter(r -> ReservationService.BOOKED.equals(r.getStatus())).findFirst().orElse(null);
		Reservation done = reservations.stream().filter(r -> ReservationService.COMPLETED.equals(r.getStatus()))
				.reduce((a, b) -> b).orElse(null);
		model.addAttribute("bookingState", upcoming == null ? "none" : "");
		if (upcoming != null) {
			model.addAttribute("booking", reservationService.view(upcoming));
		}
		if (done == null) {
			model.addAttribute("doneState", "none");
		} else {
			ReservationView view = reservationService.view(done);
			model.addAttribute("doneMove", view);
			model.addAttribute("doneState", view.reviewed() ? "reviewed" : "");
		}

		List<Quote> quotes = quoteService.memberQuotes(member.memberId());
		if (quotes.isEmpty()) {
			model.addAttribute("estimateState", "none");
		} else {
			// 가장 최근 견적을 크게, 나머지 진행 중(검토·도착) 견적은 개수만
			Quote current = quotes.get(0);
			MyQuoteView view = quoteService.myQuoteView(quoteService.detail(current.getQuoteId()), confirmedAt(current));
			model.addAttribute("myQuote", view);
			model.addAttribute("estimateState", view.state());
			model.addAttribute("otherQuotes", quotes.stream().skip(1)
					.filter(q -> QuoteService.REVIEW.equals(q.getStatus()) || QuoteService.APPROVED.equals(q.getStatus())).count());
		}
		return "user/move/index";
	}

	/** 견적 상세: ?tab=quote|ai, ?doc=open. 검토 중(01) 견적은 금액·견적서를 보여주지 않으므로 내 이사로 돌려보낸다 */
	@GetMapping("/estimates/{id}")
	public String estimateDetail(@PathVariable Long id, HttpSession session, Model model) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		QuoteService.Detail detail = quoteService.detail(id);
		if (detail == null || !member.memberId().equals(detail.quote().getMemberId())) {
			return "redirect:/move?tab=estimate";
		}
		if (QuoteService.REVIEW.equals(detail.quote().getStatus())) {
			return "redirect:/move?tab=estimate";
		}
		model.addAttribute("myQuote", quoteService.myQuoteView(detail, confirmedAt(detail.quote())));
		model.addAttribute("sheetView", quoteService.sheetView(detail));
		return "user/move/estimate-detail";
	}

	/** 견적 확정 → 예약 생성. updatedAt: 화면에서 본 견적서 수정 시각 */
	@PostMapping("/estimates/{id}/confirm")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> confirm(@PathVariable Long id, @RequestParam(defaultValue = "") String updatedAt,
			HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		try {
			reservationService.confirmQuote(member.memberId(), id, updatedAt.isBlank() ? null : LocalDateTime.parse(updatedAt));
			return ResponseEntity.ok(Map.of("redirect", "/move?tab=estimate"));
		} catch (DateTimeParseException | IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", "견적을 찾을 수 없어요."));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}

	/** 예약 상세: ?payment=open, ?edit=true, ?cancel=open */
	@GetMapping("/reservations/{id}")
	public String reservationDetail(@PathVariable Long id, HttpSession session, Model model) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		Reservation reservation = reservationService.find(member.memberId(), id);
		if (reservation == null) {
			return "redirect:/move?tab=booking";
		}
		model.addAttribute("reservation", reservationService.view(reservation));
		return "user/move/reservation-detail";
	}

	/** 요청사항 저장 */
	@PostMapping("/reservations/{id}/memo")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveMemo(@PathVariable Long id, @RequestParam(defaultValue = "") String memo,
			HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		return run(() -> reservationService.updateMemo(member.memberId(), id, memo), Map.of("ok", true));
	}

	/** 예약 취소 → 내 이사 예약 정보 */
	@PostMapping("/reservations/{id}/cancel")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id, HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		return run(() -> reservationService.cancel(member.memberId(), id), Map.of("redirect", "/move?tab=booking"));
	}

	private static ResponseEntity<Map<String, Object>> run(Runnable action, Map<String, Object> ok) {
		try {
			action.run();
			return ResponseEntity.ok(ok);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}

	/** 견적 확정 시각 = 예약 신청 시각 */
	private LocalDateTime confirmedAt(Quote q) {
		if (!QuoteService.CONFIRMED.equals(q.getStatus()) || q.getRequestId() == null) {
			return null;
		}
		Reservation r = reservationMapper.selectByRequestId(q.getRequestId());
		return r == null ? null : r.getAppliedAt();
	}
}
