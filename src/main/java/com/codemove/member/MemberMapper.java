package com.codemove.member;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.domain.Member;
import com.codemove.domain.Social;

@Mapper
public interface MemberMapper {

	/** 탈퇴 회원은 번호가 지워져 있어 찾지 않는다 (PHONE 은 UNIQUE) */
	Member selectByPhone(String phone);

	Member selectById(Long memberId);

	/** MEMBER_ID 는 SEQ_MEMBER 로 채워 member 에 넣어 준다 */
	int insertMember(Member member);

	/** 탈퇴: 이름·번호를 지우고 비활성 처리 (기록 연결용 MEMBER_ID 는 유지) */
	int withdrawMember(Long memberId);

	/** 간편 로그인 계정에 연결된 회원 */
	Member selectBySocial(@Param("loginType") String loginType, @Param("socialNo") String socialNo);

	List<Social> selectSocials(Long memberId);

	/** SOCIAL_ID 는 SEQ_SOCIAL */
	int insertSocial(Social social);

	int deleteSocials(Long memberId);
}
