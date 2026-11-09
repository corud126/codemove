package com.codemove.quote;

import java.nio.file.Path;

/**
 * 수기 견적서 사진 읽기 (SCR-A-003 '견적서를 읽고 있어요').
 * 사진에서 문서 칸을 읽어 SheetForm 으로 돌려준다. 읽지 못한 칸은 null.
 * AI(OCR) 연동 전까지는 BlankSheetReader 가 아무 칸도 채우지 않는다 → 관리자가 확인·수정 화면에서 직접 입력.
 */
public interface SheetReader {

	SheetForm read(Path photo);
}
