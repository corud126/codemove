package com.codemove.web.admin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.codemove.admin.LoginAdmin;
import com.codemove.quote.QuoteRegisterService;
import com.codemove.quote.QuoteService;
import com.codemove.quote.RegisterDraft;
import com.codemove.quote.SheetForm;
import com.codemove.quote.SheetView;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 견적 관리 SCR-A-001 ~ A-004.
 * 목록 · 견적 확인(견적 정보 · 고객 영상/원본 파일 · 견적서) · 견적서 저장 · 승인 · 삭제 · 견적서 등록(사진·PDF, QuoteRegisterService).
 * 저장·승인·삭제·등록 단계는 estimate.js 가 fetch 로 보내고 {ok} 또는 {message} 를 받는다.
 */
@Controller
@RequestMapping("/admin/estimates")
@RequiredArgsConstructor
public class AdminEstimateController {

	/** 등록 중인 견적서 (RegisterDraft) */
	static final String REGISTER_KEY = "estimateRegister";

	private final QuoteService quoteService;
	private final QuoteRegisterService registerService;

	/** SCR-A-001 견적 관리 — 행은 템플릿 형식 'id|분류|이름|YY.MM.DD|삭제 가능(Y/N)' (분류 필터·검색·페이지는 estimate.js) */
	@GetMapping
	public String list(Model model) {
		model.addAttribute("rows", quoteService.list().stream()
				.map(r -> r.id() + "|" + r.status() + "|" + r.name().replace("|", " ") + "|" + r.date() + "|" + (r.deletable() ? "Y" : "N"))
				.toList());
		return "admin/estimate/list";
	}

	/**
	 * SCR-A-003/004 견적서 등록. 단계는 ?state= (첨부 · photo-preview · analyzing · review · unrecognized · full-view · pdf-preview · pdf-review · register).
	 * 첨부 이후 단계는 세션의 등록 중인 내용(RegisterDraft)으로 그린다. 없으면(또는 단계가 맞지 않으면) 알맞은 단계로 보낸다
	 */
	@GetMapping("/new")
	public String register(@RequestParam(defaultValue = "") String state, HttpSession session, Model model) {
		RegisterDraft draft = (RegisterDraft) session.getAttribute(REGISTER_KEY);
		if (!state.isEmpty()) {
			if (draft == null || registerService.file(draft) == null) {
				return "redirect:/admin/estimates/new";
			}
			boolean pdfState = state.startsWith("pdf");
			if (pdfState != draft.isPdf() && !state.equals("register")) {
				return "redirect:/admin/estimates/new?state=" + (draft.isPdf() ? "pdf-preview" : "photo-preview");
			}
			boolean needsRead = state.equals("review") || state.equals("unrecognized") || state.equals("full-view");
			if (needsRead && !draft.read()) {
				return "redirect:/admin/estimates/new?state=photo-preview";
			}
			if (state.equals("register") && !draft.reviewed()) {
				return "redirect:/admin/estimates/new?state=" + (draft.isPdf() ? "pdf-review" : "review");
			}
			model.addAttribute("reg", registerService.view(draft, "/admin/estimates/new/file"));
			SheetView sheet = registerService.sheetView(draft);
			if (sheet != null) {
				model.addAttribute("sheetView", sheet);
			}
		}
		model.addAttribute("members", registerService.members());
		return "admin/estimate/register";
	}

	/** 1. 첨부 (multipart file): 사진이면 photo-preview, PDF 면 pdf-preview 로 */
	@PostMapping("/new/file")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> attach(@RequestParam(required = false) MultipartFile file, HttpSession session) {
		try {
			RegisterDraft draft = registerService.attach(file, (RegisterDraft) session.getAttribute(REGISTER_KEY));
			session.setAttribute(REGISTER_KEY, draft);
			return ResponseEntity.ok(Map.of("ok", true, "next", draft.isPdf() ? "pdf-preview" : "photo-preview"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	/** 첨부한 원본 (확인 화면 미리보기) */
	@GetMapping("/new/file")
	public ResponseEntity<Resource> attachedFile(HttpSession session) {
		RegisterDraft draft = (RegisterDraft) session.getAttribute(REGISTER_KEY);
		Path file = registerService.file(draft);
		return file == null ? ResponseEntity.notFound().build() : serve(file, draft.fileName(), false);
	}

	/** 사진 읽기 (읽는 중 화면에서 호출): 다음 단계 review · unrecognized */
	@PostMapping("/new/read")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> read(HttpSession session) {
		try {
			RegisterDraft draft = registerService.read((RegisterDraft) session.getAttribute(REGISTER_KEY));
			session.setAttribute(REGISTER_KEY, draft);
			return ResponseEntity.ok(Map.of("ok", true, "next", QuoteRegisterService.nextAfterRead(draft)));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}

	/** 2. 사진 확인·수정 값 (SheetForm, 견적서 수정과 같은 형식) */
	@PostMapping("/new/sheet")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> reviewSheet(@RequestBody SheetForm form, HttpSession session) {
		return run(() -> session.setAttribute(REGISTER_KEY,
				registerService.review((RegisterDraft) session.getAttribute(REGISTER_KEY), form)));
	}

	/** 2. PDF 정보 입력 */
	@PostMapping("/new/pdf")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> reviewPdf(@RequestBody RegisterDraft.PdfForm form, HttpSession session) {
		return run(() -> session.setAttribute(REGISTER_KEY,
				registerService.reviewPdf((RegisterDraft) session.getAttribute(REGISTER_KEY), form)));
	}

	/** 등록 요청: memberId(선택) · status 01 견적 검토 · 02 견적 승인 · 03 견적 확정 */
	public record RegisterForm(Long memberId, String status) {
	}

	/** 3. 견적 관리에 등록 → {ok, id, name} */
	@PostMapping("/new")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> registerQuote(@RequestBody RegisterForm form, HttpSession session) {
		RegisterDraft draft = (RegisterDraft) session.getAttribute(REGISTER_KEY);
		LoginAdmin admin = (LoginAdmin) session.getAttribute(AdminAuthController.ADMIN_KEY);
		try {
			Long id = registerService.register(draft, form.memberId(), form.status(), admin.adminId());
			session.removeAttribute(REGISTER_KEY);
			String name = draft.isPdf() ? draft.pdf().name().trim() : draft.sheet().customer().trim();
			return ResponseEntity.ok(Map.of("ok", true, "id", id, "name", name));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
		}
	}

	/** 원본 파일 (사진·PDF 등록 견적의 원본 파일 탭). ?download=1 이면 내려받기 */
	@GetMapping("/{id:\\d+}/file")
	public ResponseEntity<Resource> document(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
		Path file = quoteService.documentFile(id);
		return file == null ? ResponseEntity.notFound().build() : serve(file, file.getFileName().toString(), download);
	}

	private static ResponseEntity<Resource> serve(Path file, String fileName, boolean download) {
		MediaType type = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
		ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
				.filename(fileName, StandardCharsets.UTF_8).build();
		return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(new FileSystemResource(file));
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
