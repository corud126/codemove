package com.codemove.quote;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.Quote;
import com.codemove.domain.QuoteItem;
import com.codemove.domain.QuoteOption;

@Mapper
public interface QuoteMapper {

	/** QUOTE_ID 는 SEQ_QUOTE, QUOTE_NO 는 QT-견적일-QUOTE_ID 끝 4자리 */
	int insertQuote(Quote quote);

	int insertQuoteItem(QuoteItem item);

	Quote selectByRequestId(Long requestId);

	Quote selectById(Long quoteId);

	/** 견적 관리 목록 (견적일 · 번호 내림차순) */
	List<Quote> selectAll();

	List<QuoteItem> selectItems(Long quoteId);

	List<QuoteOption> selectOptions(Long quoteId);

	/** 견적서 본문 저장 (상태·승인 관리자는 그대로, UPDATED_AT 기록). 견적 확정(03)이면 0건 */
	int updateSheet(Quote quote);

	int deleteItems(Long quoteId);

	int deleteOptions(Long quoteId);

	int insertOption(QuoteOption option);

	/** 견적 승인 (재승인 포함): STATUS 02, APPROVED_AT, EXPIRES_AT, ADMIN_ID */
	int approve(Quote quote);

	/** 만료 시각이 지난 견적 승인 → 만료 */
	int expireOverdue();

	/** 회원의 견적 (최근 순) */
	List<Quote> selectByMember(Long memberId);

	/**
	 * 사용자 견적 확정: 견적 승인(02) · 만료 전 · 화면에서 본 UPDATED_AT 과 같을 때만 03 으로 (ERD 동시 수정 방지).
	 * 바뀐 건이 없으면 0
	 */
	int confirm(@Param("quoteId") Long quoteId, @Param("updatedAt") java.time.LocalDateTime updatedAt);

	int deleteQuote(Long quoteId);

	/**
	 * 관리자 견적서 등록 (사진 02 · PDF 03): 견적서 본문 · 원본 경로 · 회원 연결(선택) · 상태를 한 번에 넣는다.
	 * QUOTE_ID 는 SEQ_QUOTE, QUOTE_NO 는 QT-등록일-QUOTE_ID 끝 4자리. 승인·확정으로 등록하면 APPROVED_AT 은 등록 시각
	 */
	int insertRegistered(Quote quote);

	/** 견적서 등록 회원 연결 목록: 탈퇴하지 않은 회원 (가입 최근 순) */
	List<com.codemove.domain.Member> selectActiveMembers();
}
