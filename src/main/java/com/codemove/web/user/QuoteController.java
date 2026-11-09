package com.codemove.web.user;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.address.AddressSearch;
import com.codemove.common.code.CodeService;
import com.codemove.member.LoginMember;
import com.codemove.request.ItemCatalog;
import com.codemove.request.MoveRequestService;
import com.codemove.request.QuoteForm;
import com.codemove.request.QuoteProperties;
import com.codemove.request.QuoteSession;
import com.codemove.request.VideoAnalyzer;
import com.codemove.request.VideoStorage;
import com.codemove.schedule.AvailabilityService;
import com.codemove.schedule.MonthAvailability;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이사 견적 (SCR-U-003~010).
 * 6단계 화면은 quote.js 가 진행하고, 서버는 영상 저장 · AI 분석 · 주소 검색 · 날짜 휴무/마감 · 예상 금액 · 요청 제출을 맡는다.
 * 영상과 분석 결과는 요청을 낼 때까지 세션(QuoteSession)에 둔다. ?step=1~6&state= 캡처 상태는 그대로 동작.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class QuoteController {

	private final ItemCatalog catalog;
	private final VideoStorage videoStorage;
	private final VideoAnalyzer videoAnalyzer;
	private final AddressSearch addressSearch;
	private final AvailabilityService availabilityService;
	private final MoveRequestService requestService;
	private final CodeService codeService;
	private final QuoteProperties props;

	@GetMapping("/quote")
	public String quote(Model model) {
		model.addAttribute("pickable", catalog.pickable());
		model.addAttribute("catalog", catalog.forScript());
		model.addAttribute("today", LocalDate.now().toString());
		return "user/quote/index";
	}

	@GetMapping("/quote/complete")
	public String complete() {
		return "user/quote/complete";
	}

	/** 1단계: 영상 저장 (다른 영상으로 바꾸면 이전 파일과 분석 결과는 버린다) */
	@PostMapping("/quote/video")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> uploadVideo(@RequestParam("video") MultipartFile video, HttpSession session) {
		String type = video.getContentType();
		if (video.isEmpty() || type == null || !type.startsWith("video/")) {
			return fail(HttpStatus.BAD_REQUEST, "동영상 파일을 선택해 주세요.");
		}
		String name = video.getOriginalFilename() == null ? "video" : Path.of(video.getOriginalFilename()).getFileName().toString();
		if (name.length() > 200) {
			name = name.substring(name.length() - 200);
		}
		Path saved;
		try {
			saved = videoStorage.save(video.getInputStream(), name);
		} catch (IOException e) {
			log.warn("영상 저장 실패", e);
			return fail(HttpStatus.INTERNAL_SERVER_ERROR, "영상을 올리지 못했어요. 다시 시도해 주세요.");
		}
		QuoteSession previous = (QuoteSession) session.getAttribute(QuoteSession.KEY);
		if (previous != null) {
			videoStorage.delete(previous.videoPath());
		}
		session.setAttribute(QuoteSession.KEY, new QuoteSession(name, saved.toString(), null, null));
		return ResponseEntity.ok(Map.of("fileName", name));
	}

	/** 2단계: AI 분석. 품목 이름·크기는 화면 표시명으로, aiIndex 는 제출 때 신뢰도를 찾는 순번 */
	@PostMapping("/quote/analyze")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> analyze(HttpSession session) {
		QuoteSession current = (QuoteSession) session.getAttribute(QuoteSession.KEY);
		if (current == null) {
			return fail(HttpStatus.BAD_REQUEST, "영상을 먼저 올려 주세요.");
		}
		long started = System.currentTimeMillis();
		List<VideoAnalyzer.Detected> detected = videoAnalyzer.analyze(Path.of(current.videoPath()));
		int seconds = (int) Math.max(1, (System.currentTimeMillis() - started + 999) / 1000);
		session.setAttribute(QuoteSession.KEY, current.analyzed(detected, seconds));

		List<Map<String, Object>> items = new java.util.ArrayList<>();
		for (int i = 0; i < detected.size(); i++) {
			VideoAnalyzer.Detected d = detected.get(i);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", d.name());
			item.put("size", codeService.userName("ITEM_SIZE", d.sizeCode()));
			item.put("count", d.quantity());
			item.put("review", d.confidence() < props.reviewConfidence());
			item.put("aiIndex", i);
			items.add(item);
		}
		return ResponseEntity.ok(Map.of("items", items));
	}

	/** 4단계: 주소 검색 */
	@GetMapping("/quote/address")
	@ResponseBody
	public List<AddressSearch.Address> address(@RequestParam(defaultValue = "") String q) {
		return addressSearch.search(q);
	}

	/** 5단계: 달 단위 휴무 · 마감 · 공휴일 (month=yyyy-MM) */
	@GetMapping("/quote/calendar")
	@ResponseBody
	public ResponseEntity<Object> calendar(@RequestParam String month) {
		try {
			MonthAvailability m = availabilityService.month(YearMonth.parse(month));
			return ResponseEntity.ok(Map.of("closed", m.closed(), "full", m.full(), "holidays", m.holidays()));
		} catch (DateTimeParseException e) {
			return ResponseEntity.badRequest().build();
		}
	}

	/** 6단계: 예상 금액 */
	@PostMapping("/quote/estimate")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> estimate(@RequestBody QuoteForm form, HttpSession session) {
		try {
			MoveRequestService.Calculation calc = requestService.calculate(form,
					(QuoteSession) session.getAttribute(QuoteSession.KEY));
			return ResponseEntity.ok(Map.of("amount", calc.estimate().amount(), "min", calc.estimate().min(),
					"max", calc.estimate().max(), "count", calc.count(), "cbm", calc.cbm()));
		} catch (IllegalArgumentException e) {
			return fail(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/** 견적 요청 제출 → 견적 요청 완료 */
	@PostMapping("/quote")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> submit(@RequestBody QuoteForm form, HttpSession session) {
		LoginMember member = (LoginMember) session.getAttribute(HomeController.LOGIN_KEY);
		try {
			requestService.submit(member, form, (QuoteSession) session.getAttribute(QuoteSession.KEY));
		} catch (IllegalArgumentException e) {
			return fail(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		session.removeAttribute(QuoteSession.KEY);
		return ResponseEntity.ok(Map.of("redirect", "/quote/complete"));
	}

	private static ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("message", message));
	}
}
