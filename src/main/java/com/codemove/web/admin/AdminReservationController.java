package com.codemove.web.admin;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.reservation.ReservationService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 예약 관리 SCR-A-011.
 * 목록(분류 필터 · 검색 · 페이지는 reservation.js) · 상세 · [결제 완료] · [예약 취소] · [일정 변경].
 * 동작은 reservation.js 가 fetch 로 보내고 {ok, ...} 또는 {message} 를 받는다. ?state= 미리보기는 그대로 동작한다.
 */
@Controller
@RequestMapping("/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

	private final ReservationService reservationService;

	/**
	 * 행은 템플릿 형식 'id|예약 상태|이름|휴대폰|신청일|이사 유형|포장 방식|이사일|이사 시간|출발지|도착지|결제 상태|총액|이사 메모|계약금 결제일|취소일|취소 주체|계약금|잔금|견적 번호|예약 번호'
	 * (빈 값은 '-', 글자 안의 '|' 와 줄바꿈은 공백으로)
	 */
	@GetMapping
	public String list(Model model) {
		model.addAttribute("rows", reservationService.adminRows().stream()
				.map(r -> Stream.of(String.valueOf(r.id()), r.status(), r.name(), r.phone(), r.applied(), r.service(), r.pack(),
						r.moveDate(), r.moveTime(), r.origin(), r.destination(), r.payment(), r.total(), r.memo(), r.paidAt(),
						r.cancelledAt(), r.cancelledBy(), r.deposit(), r.balance(), r.quoteNo(), r.reservationNo())
						.map(s -> s == null ? "" : s.replaceAll("[|\\r\\n]+", " ").trim())
						.map(s -> s.isEmpty() ? "-" : s)
						.collect(Collectors.joining("|")))
				.toList());
		return "admin/reservation/list";
	}

	/** [결제 완료] → {ok, paidAt} */
	@PostMapping("/{id:\\d+}/paid")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> paid(@PathVariable Long id) {
		return run(() -> Map.of("ok", true, "paidAt", reservationService.completePayment(id)));
	}

	/** [예약 취소] → {ok, cancelledAt} */
	@PostMapping("/{id:\\d+}/cancel")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id) {
		return run(() -> Map.of("ok", true, "cancelledAt", reservationService.cancelByAdmin(id)));
	}

	/** [일정 변경] {date: 'YY.MM.DD', time: 'HH:MM'} → {ok} */
	@PostMapping("/{id:\\d+}/schedule")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> schedule(@PathVariable Long id, @RequestBody Map<String, String> body) {
		return run(() -> {
			reservationService.reschedule(id, body.get("date"), body.get("time"));
			return Map.of("ok", true);
		});
	}

	/** 서비스가 던진 안내 문구를 화면에 그대로 돌려준다 */
	private static ResponseEntity<Map<String, Object>> run(Supplier<Map<String, Object>> action) {
		try {
			return ResponseEntity.ok(action.get());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}
}
