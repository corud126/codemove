package com.codemove.web.admin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.admin.LoginAdmin;
import com.codemove.common.code.CodeService;
import com.codemove.common.holiday.HolidayProvider;
import com.codemove.domain.ClosedDay;
import com.codemove.schedule.CalendarReservation;
import com.codemove.schedule.ScheduleService;
import com.codemove.schedule.ScheduleService.ScheduleException;
import com.codemove.schedule.ScheduleSetting;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 일정 SCR-A-020 예약 일정(예약을 달력으로 조회, 보기 전용) · SCR-A-022 일정 관리(설정: 하루 최대 예약 · 정기/공휴일/임시 휴무, 연동: 공휴일 · 손 없는 날).
 * 일정 관리의 설정은 사용자 견적 날짜 선택(휴무일 · 마감일 선택 불가)과 연결된다(AvailabilityService). 개인 일정 등록(SCR-A-021)은 뺐다.
 * 화면은 schedule.js 가 아래 JSON 을 fetch 로 불러 그린다. 컨트롤러 없이 /dev/admin/schedule/... 로 열면 더미 데이터(?state= 미리보기).
 */
@Controller
@RequestMapping("/admin/schedule")
@RequiredArgsConstructor
public class AdminScheduleController {

	/** 화면 날짜 키 (26.09.27) */
	private static final DateTimeFormatter KEY = DateTimeFormatter.ofPattern("yy.MM.dd");

	private final ScheduleService scheduleService;
	private final HolidayProvider holidayProvider;
	private final CodeService codeService;

	@GetMapping
	public String calendar(Model model) {
		live(model);
		return "admin/schedule/calendar";
	}

	@GetMapping("/manage")
	public String manage(Model model) {
		live(model);
		return "admin/schedule/manage";
	}

	private static void live(Model model) {
		model.addAttribute("live", true);
		model.addAttribute("today", LocalDate.now().format(KEY));
	}

	/** 예약 설정 + 임시 휴무 목록(그날 예약 확정 건수 포함) */
	@GetMapping("/settings")
	@ResponseBody
	public Map<String, Object> settings() {
		ScheduleSetting setting = scheduleService.setting();
		List<ClosedDay> days = scheduleService.closedDays();
		Map<LocalDate, Integer> counts = days.isEmpty() ? Map.of()
				: scheduleService.confirmedCounts(days.get(0).getClosedDate(),
						days.get(days.size() - 1).getClosedDate().plusDays(1));
		List<Map<String, Object>> closed = new ArrayList<>();
		for (ClosedDay d : days) {
			closed.add(Map.of("date", d.getClosedDate().toString(), "booked", counts.getOrDefault(d.getClosedDate(), 0)));
		}
		Map<String, Object> body = settingBody(setting);
		body.put("closedDays", closed);
		return body;
	}

	/** 한 달 예약(예약 완료 · 이사 완료) · 공휴일 · 손 없는 날. ym = 2026-09 */
	@GetMapping("/month")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> month(@RequestParam(defaultValue = "") String ym) {
		YearMonth month;
		try {
			month = YearMonth.parse(ym);
		} catch (DateTimeParseException e) {
			return fail("달을 다시 확인해 주세요.");
		}
		List<Map<String, Object>> reservations = new ArrayList<>();
		for (CalendarReservation r : scheduleService.calendarReservations(month.atDay(1), month.plusMonths(1).atDay(1))) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", r.getReservationId());
			m.put("reservationNo", r.getReservationNo());
			m.put("name", Objects.toString(r.getMemberName(), ""));
			m.put("phone", Objects.toString(r.getPhone(), ""));
			m.put("date", r.getMoveDate().toString());
			m.put("time", Objects.toString(r.getMoveTime(), ""));
			m.put("service", Objects.toString(codeService.name("HOUSE_TYPE", r.getHouseType()), ""));
			m.put("origin", Objects.toString(r.getFromAddress(), ""));
			m.put("destination", Objects.toString(r.getToAddress(), ""));
			m.put("status", Objects.toString(codeService.name("RSV_STATUS", r.getStatus()), ""));
			m.put("payStatus", Objects.toString(codeService.name("PAY_STATUS", r.getPayStatus()), ""));
			reservations.add(m);
		}
		List<Map<String, Object>> holidays = holidayProvider.holidays(month).stream()
				.map(h -> Map.<String, Object>of("date", h.date().toString(), "name", h.name())).toList();
		List<String> noMove = holidayProvider.noMoveDays(month).stream().map(LocalDate::toString).toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("reservations", reservations);
		body.put("holidays", holidays);
		body.put("noMove", noMove);
		return ResponseEntity.ok(body);
	}

	/** 예약 설정 저장: daily 1~10, weekdays '0,1'(0 일 ~ 6 토, 비우면 없음), holiday true/false */
	@PostMapping("/setting")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveSetting(@RequestParam(defaultValue = "0") int daily,
			@RequestParam(defaultValue = "") String weekdays, @RequestParam(defaultValue = "false") boolean holiday,
			HttpSession session) {
		if (!weekdays.isBlank() && !weekdays.matches("\\s*[0-6](\\s*,\\s*[0-6])*\\s*")) {
			return fail("정기 휴무 요일을 다시 확인해 주세요.");
		}
		Set<Integer> days = ScheduleSetting.parseWeekdays(weekdays);
		try {
			return ResponseEntity.ok(settingBody(scheduleService.saveSetting(daily, days, holiday, adminId(session))));
		} catch (ScheduleException e) {
			return fail(e.getMessage());
		}
	}

	/** 임시 휴무 등록 · 수정: date = 2026-10-09, before = 수정 전 날짜(등록이면 비움). 그날 예약 확정 건수를 돌려준다 */
	@PostMapping("/closed-days")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> addClosedDay(@RequestParam(defaultValue = "") String date,
			@RequestParam(defaultValue = "") String before, HttpSession session) {
		LocalDate day = parseDate(date);
		LocalDate prev = before.isBlank() ? null : parseDate(before);
		if (day == null || (!before.isBlank() && prev == null)) {
			return fail("날짜를 다시 확인해 주세요.");
		}
		try {
			int booked = scheduleService.addClosedDay(day, prev, adminId(session));
			return ResponseEntity.ok(Map.of("date", day.toString(), "booked", booked));
		} catch (ScheduleException e) {
			return fail(e.getMessage());
		}
	}

	/** 임시 휴무 해제 */
	@PostMapping("/closed-days/delete")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> removeClosedDay(@RequestParam(defaultValue = "") String date) {
		LocalDate day = parseDate(date);
		if (day == null) {
			return fail("날짜를 다시 확인해 주세요.");
		}
		if (!scheduleService.removeClosedDay(day)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "이미 해제된 날짜예요."));
		}
		return ResponseEntity.ok(Map.of("date", day.toString()));
	}

	private static Map<String, Object> settingBody(ScheduleSetting setting) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("daily", setting.dailyCapacity());
		body.put("weekdays", setting.weekdayList());
		body.put("holiday", setting.holidayClosed());
		return body;
	}

	private static Long adminId(HttpSession session) {
		LoginAdmin admin = (LoginAdmin) session.getAttribute(AdminAuthController.ADMIN_KEY);
		return admin != null ? admin.adminId() : null;
	}

	private static LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value.trim());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static ResponseEntity<Map<String, Object>> fail(String message) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
	}
}
