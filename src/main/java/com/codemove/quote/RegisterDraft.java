package com.codemove.quote;

import java.io.Serializable;

/**
 * 견적서 등록 중인 내용 (SCR-A-003/004, 세션 보관 — 단계 이동은 ?state= 주소라 화면을 다시 그려도 이어진다).
 * source: photo · pdf, filePath: 첨부 파일(등록 전 임시 위치), fileName: 원래 파일 이름
 * read: 사진 읽기를 마쳤는지, sheet: 사진 확인·수정 값, pdf: PDF 정보 입력 값, reviewed: 확인·수정 값을 검증해 둔 상태(등록 단계로 갈 수 있음)
 */
public record RegisterDraft(String source, String filePath, String fileName, boolean read, SheetForm sheet,
		PdfForm pdf, boolean reviewed) implements Serializable {

	/** PDF 등록 입력: 이름 · 연락처 · 견적일(YY.MM.DD) · 총 비용 */
	public record PdfForm(String name, String phone, String estimateDate, String total) implements Serializable {
	}

	public boolean isPdf() {
		return "pdf".equals(source);
	}

	RegisterDraft withSheet(SheetForm form, boolean read, boolean reviewed) {
		return new RegisterDraft(source, filePath, fileName, read, form, pdf, reviewed);
	}

	RegisterDraft withPdf(PdfForm form) {
		return new RegisterDraft(source, filePath, fileName, read, sheet, form, true);
	}
}
