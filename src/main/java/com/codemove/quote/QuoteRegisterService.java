package com.codemove.quote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.code.CodeService;
import com.codemove.domain.Member;
import com.codemove.domain.Quote;
import com.codemove.member.MemberService;
import com.codemove.quote.RegisterDraft.PdfForm;
import com.codemove.quote.SheetView.OptionRow;
import com.codemove.quote.SheetView.TransportRow;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 견적서 등록 SCR-A-003/004 (과거 수기 견적서를 사진·PDF 로 등록, ERD Q3).
 * 흐름: 첨부(attach) → 사진은 읽기(read, AI 연동 전이라 빈 칸) → 확인·수정(review / reviewPdf, 검증만) → 등록(register).
 * 등록 중인 내용은 컨트롤러가 세션(RegisterDraft)에 둔다. 만료 규칙은 견적 요청(01)에만 있어 등록 견적은 만료 시각이 없다.
 * 입력이 잘못되면 화면에 보여줄 문구로 IllegalArgumentException, 단계가 맞지 않으면 IllegalStateException.
 */
@Service
@RequiredArgsConstructor
public class QuoteRegisterService {

	public static final String SOURCE_PHOTO = "02";
	public static final String SOURCE_PDF = "03";
	private static final Set<String> STATUSES = Set.of(QuoteService.REVIEW, QuoteService.APPROVED, QuoteService.CONFIRMED);
	private static final String COMPANY = "명품익스프레스";
	private static final List<String> GROUP_NAMES = List.of("전자기기", "가구", "생활용품", "기타");
	private static final List<String> TRANSPORT_NAMES = List.of("사다리차", "엘리베이터", "계단");
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yy.MM.dd");

	private final QuoteService quoteService;
	private final QuoteMapper quoteMapper;
	private final QuoteFileStore fileStore;
	private final SheetReader sheetReader;
	private final MemberService memberService;
	private final CodeService codeService;

	/** 1. 첨부: 새 파일을 임시 위치에 두고, 이전에 첨부한 파일은 지운다 */
	public RegisterDraft attach(MultipartFile file, RegisterDraft previous) {
		QuoteFileStore.Attached a = fileStore.attach(file);
		discard(previous);
		return new RegisterDraft(a.source(), a.path().toString(), a.fileName(), false, null, null, false);
	}

	/** 등록을 그만두거나 새로 첨부하면 임시 파일 정리 */
	public void discard(RegisterDraft draft) {
		if (draft != null) {
			fileStore.delete(draft.filePath());
		}
	}

	/** 첨부 파일 (없으면 null) */
	public Path file(RegisterDraft draft) {
		if (draft == null) {
			return null;
		}
		Path p = Path.of(draft.filePath());
		return Files.isRegularFile(p) ? p : null;
	}

	/** 사진 읽기: 읽은 칸으로 확인·수정 값을 채운다 (읽지 못한 칸은 비움, 업체명은 기본값) */
	public RegisterDraft read(RegisterDraft draft) {
		if (draft == null || draft.isPdf()) {
			throw new IllegalStateException("견적서 사진을 다시 첨부해 주세요.");
		}
		SheetForm read = file(draft) == null ? null : sheetReader.read(Path.of(draft.filePath()));
		SheetForm f = read == null ? blankSheet() : read;
		if (QuoteService.blank(f.companyName())) {
			f = new SheetForm(COMPANY, f.customer(), f.phone(), f.estimateDate(), f.origin(), f.moveDate(), f.destination(),
					f.transportDate(), f.items(), f.options(), f.workMethod(), f.transports(), f.volume(), f.note(),
					f.movingCost(), f.fromLadderFloor(), f.departureLadder(), f.toLadderFloor(), f.destinationLadder());
		}
		return draft.withSheet(f, true, false);
	}

	/** 읽은 뒤 갈 단계: 꼭 필요한 칸(담당자·견적일·도착지)이 비면 '확인이 필요한 항목' 화면 */
	public static String nextAfterRead(RegisterDraft draft) {
		SheetForm f = draft.sheet();
		boolean missing = f == null || QuoteService.blank(f.customer()) || QuoteService.blank(f.estimateDate())
				|| QuoteService.blank(f.destination());
		return missing ? "unrecognized" : "review";
	}

	/** 2. 사진 확인·수정 값 검증 후 보관 */
	public RegisterDraft review(RegisterDraft draft, SheetForm form) {
		if (draft == null || draft.isPdf() || !draft.read()) {
			throw new IllegalStateException("견적서 사진을 다시 첨부해 주세요.");
		}
		if (form == null) {
			throw new IllegalArgumentException("견적 내용을 입력해 주세요.");
		}
		quoteService.applySheet(new Quote(), form);
		return draft.withSheet(form, true, true);
	}

	/** 2. PDF 정보(이름 · 연락처 · 견적일 · 총 비용) 검증 후 보관 */
	public RegisterDraft reviewPdf(RegisterDraft draft, PdfForm form) {
		if (draft == null || !draft.isPdf()) {
			throw new IllegalStateException("견적서 PDF를 다시 첨부해 주세요.");
		}
		if (form == null) {
			throw new IllegalArgumentException("이름을 입력해 주세요.");
		}
		applyPdf(new Quote(), form);
		return draft.withPdf(form);
	}

	/**
	 * 3. 등록: 회원 연결(선택) · 분류(01 견적 검토 · 02 견적 승인 · 03 견적 확정)로 견적 관리에 추가하고 원본을 옮긴다.
	 * 회원을 연결하면 MEMBER_ID 를 두고, 연락처가 비어 있으면 회원 번호를 쓴다. 등록한 견적 ID
	 */
	@Transactional
	public Long register(RegisterDraft draft, Long memberId, String status, Long adminId) {
		if (draft == null || !draft.reviewed()) {
			throw new IllegalStateException("견적 내용을 먼저 확인해 주세요.");
		}
		if (!STATUSES.contains(status)) {
			throw new IllegalArgumentException("분류를 다시 선택해 주세요.");
		}
		Path attached = file(draft);
		if (attached == null) {
			throw new IllegalStateException("첨부한 파일을 찾을 수 없어요. 다시 첨부해 주세요.");
		}
		Quote q = new Quote();
		q.setSourceType(draft.isPdf() ? SOURCE_PDF : SOURCE_PHOTO);
		q.setStatus(status);
		QuoteService.Parsed parsed = draft.isPdf() ? null : quoteService.applySheet(q, draft.sheet());
		if (draft.isPdf()) {
			applyPdf(q, draft.pdf());
		}
		if (memberId != null) {
			Member member = memberService.findActiveById(memberId);
			if (member == null) {
				throw new IllegalArgumentException("연결할 회원을 찾을 수 없어요.");
			}
			q.setMemberId(member.getMemberId());
			if (q.getContactPhone() == null) {
				q.setContactPhone(member.getPhone());
			}
		}
		// 승인·확정으로 등록하면 등록한 관리자가 견적서 서명 담당
		q.setAdminId(QuoteService.REVIEW.equals(status) ? null : adminId);
		Path target = fileStore.target(draft.fileName());
		q.setDocumentPath(target.toString());
		quoteMapper.insertRegistered(q);
		if (parsed != null) {
			quoteService.insertLines(q.getQuoteId(), parsed);
		}
		fileStore.move(attached, target);
		// 알림 단계에서 추가: 회원을 연결해 견적 승인으로 등록하면 고객에게 '견적 도착' 알림
		return q.getQuoteId();
	}

	/** PDF 입력값을 견적서에 옮긴다. 총 비용을 넣으면 계약금·잔금도 계산 */
	private void applyPdf(Quote q, PdfForm f) {
		q.setCompanyName(COMPANY);
		q.setContactName(QuoteService.required(f.name(), 50, "이름"));
		q.setContactPhone(QuoteService.optional(f.phone(), 20, "연락처"));
		q.setEstimateDate(QuoteService.requiredDate(f.estimateDate(), "견적일"));
		if (QuoteService.blank(f.total())) {
			q.setTotalAmount(null);
			q.setDepositAmount(null);
			q.setBalanceAmount(null);
		} else {
			quoteService.setAmounts(q, QuoteService.parseMoney(f.total()));
		}
	}

	/* ---------- 화면 표시값 ---------- */

	/** 회원 연결 목록 (탈퇴 회원 제외) */
	public List<RegisterView.MemberChoice> members() {
		return quoteMapper.selectActiveMembers().stream()
				.map(m -> new RegisterView.MemberChoice(m.getMemberId(), m.getMemberName(), m.getPhone(),
						m.getJoinedAt() == null ? "" : m.getJoinedAt().format(YMD)))
				.toList();
	}

	/** 등록 단계 문서 미리보기 (사진 등록, 확인·수정을 마친 뒤). 그 밖에는 null */
	public SheetView sheetView(RegisterDraft draft) {
		if (draft == null || draft.isPdf() || !draft.reviewed()) {
			return null;
		}
		Quote q = new Quote();
		QuoteService.Parsed parsed = quoteService.applySheet(q, draft.sheet());
		return quoteService.sheetView(new QuoteService.Detail(q, parsed.items(), parsed.options(), null, List.of(), null));
	}

	public RegisterView view(RegisterDraft draft, String fileUrl) {
		SheetForm f = draft.sheet() == null ? blankSheet() : draft.sheet();
		PdfForm p = draft.pdf() == null ? new PdfForm("", "", "", "") : draft.pdf();
		Map<String, String> v = new LinkedHashMap<>();
		v.put("companyName", nv(f.companyName()));
		v.put("customer", nv(f.customer()));
		v.put("phone", nv(f.phone()));
		v.put("fromAddress", nv(f.origin()));
		v.put("toAddress", nv(f.destination()));
		v.put("estimateDate", nv(f.estimateDate()));
		v.put("workDate", nv(f.moveDate()));
		v.put("transportDate", nv(f.transportDate()));
		v.put("workMethod", nv(f.workMethod()));
		v.put("volume", nv(f.volume()).replace("톤", "").trim());
		v.put("note", nv(f.note()));
		v.put("movingCost", moneyText(f.movingCost()));
		v.put("fromLadderFloor", nv(f.fromLadderFloor()));
		v.put("fromLadderCost", moneyText(f.departureLadder()));
		v.put("toLadderFloor", nv(f.toLadderFloor()));
		v.put("toLadderCost", moneyText(f.destinationLadder()));

		List<OptionRow> options = new ArrayList<>();
		long optionSum = 0;
		for (int i = 0; i < QuoteService.OPTION_CHOICES.size(); i++) {
			SheetForm.OptionForm o = f.options() != null && i < f.options().size() ? f.options().get(i) : null;
			long cost = o == null ? 0 : lenient(o.cost());
			optionSum += cost;
			options.add(new OptionRow(codeService.name("QUOTE_OPTION", String.format("%02d", i + 1)),
					QuoteService.OPTION_CHOICES.get(i)[0], QuoteService.OPTION_CHOICES.get(i)[1], o != null && o.on1(),
					o != null && o.on2(), cost == 0 ? "" : QuoteService.money(cost)));
		}
		long total = lenient(f.movingCost()) + lenient(f.departureLadder()) + lenient(f.destinationLadder()) + optionSum;
		long deposit = Math.round(total * 0.1);
		v.put("optionSum", QuoteService.money(optionSum));
		v.put("total", QuoteService.money(total));
		v.put("deposit", QuoteService.money(deposit));
		v.put("balance", QuoteService.money(total - deposit));

		v.put("pdfName", nv(p.name()));
		v.put("pdfPhone", nv(p.phone()));
		v.put("pdfEstimateDate", nv(p.estimateDate()));
		v.put("pdfTotal", moneyText(p.total()));
		if (draft.isPdf()) {
			v.put("registerName", nv(p.name()));
			v.put("registerLine", "견적일 " + nv(p.estimateDate()) + (lenient(p.total()) > 0 ? " · " + moneyText(p.total()) + "원" : ""));
		} else {
			v.put("registerName", nv(f.customer()));
			v.put("registerLine", "견적일 " + nv(f.estimateDate()) + " · " + QuoteService.money(total) + "원");
		}

		List<TransportRow> transports = new ArrayList<>();
		for (int i = 0; i < TRANSPORT_NAMES.size(); i++) {
			SheetForm.TransportForm t = f.transports() != null && i < f.transports().size() ? f.transports().get(i) : null;
			transports.add(new TransportRow(TRANSPORT_NAMES.get(i), t != null && t.from(), t != null && t.to()));
		}
		return new RegisterView(draft.source(), draft.fileName(), fileUrl, draft.read() ? nextAfterRead(draft) : "review", v,
				transports, groups(f.items()), options);
	}

	/** 문서 품목 행(8칸) → 그룹 4개 (칸 안에서는 위에서 아래로). 수량 '6대' → 6 · 대 */
	private static List<RegisterView.ItemGroup> groups(List<List<String>> rows) {
		List<RegisterView.ItemGroup> groups = new ArrayList<>();
		for (int col = 0; col < GROUP_NAMES.size(); col++) {
			List<RegisterView.Item> items = new ArrayList<>();
			if (rows != null) {
				for (List<String> row : rows) {
					String name = row.size() > col * 2 ? row.get(col * 2) : null;
					if (QuoteService.blank(name)) {
						continue;
					}
					String qty = row.size() > col * 2 + 1 && row.get(col * 2 + 1) != null ? row.get(col * 2 + 1).trim() : "";
					String digits = qty.replaceAll("^(\\d*).*$", "$1");
					items.add(new RegisterView.Item(name.trim(), digits, qty.substring(digits.length()).trim()));
				}
			}
			groups.add(new RegisterView.ItemGroup(GROUP_NAMES.get(col), items));
		}
		return groups;
	}

	private static SheetForm blankSheet() {
		return new SheetForm(COMPANY, null, null, null, null, null, null, null, List.of(), List.of(), null, List.of(), null,
				null, null, null, null, null, null);
	}

	/** 숫자만 골라 읽기 (화면 합계용 — 검증은 저장할 때) */
	private static long lenient(String text) {
		String digits = text == null ? "" : text.replaceAll("\\D", "");
		return digits.isEmpty() || digits.length() > 10 ? 0 : Long.parseLong(digits);
	}

	private static String moneyText(String text) {
		long n = lenient(text);
		return n == 0 ? (text != null && text.replaceAll("\\D", "").startsWith("0") ? "0" : "") : QuoteService.money(n);
	}

	private static String nv(String s) {
		return s == null ? "" : s;
	}
}
