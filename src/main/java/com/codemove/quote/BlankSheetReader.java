package com.codemove.quote;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

/** 임시 사진 읽기: AI(OCR) 연동 전이라 어떤 칸도 읽지 않는다 (확인·수정 화면에서 관리자가 입력) */
@Component
public class BlankSheetReader implements SheetReader {

	@Override
	public SheetForm read(Path photo) {
		return null;
	}
}
