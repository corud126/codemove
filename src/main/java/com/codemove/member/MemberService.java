package com.codemove.member;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.Member;
import com.codemove.domain.Social;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberMapper memberMapper;

	/** 휴대폰 번호를 010-0000-0000 형식으로 맞춘다. 휴대폰 번호(01X + 10~11자리)가 아니면 null */
	public static String normalizePhone(String raw) {
		if (raw == null) {
			return null;
		}
		String n = raw.replaceAll("\\D", "");
		if (!n.matches("01\\d{8,9}")) {
			return null;
		}
		int mid = n.length() - 4;
		return n.substring(0, 3) + "-" + n.substring(3, mid) + "-" + n.substring(mid);
	}

	/** 이미 가입된 번호인지 */
	public boolean isRegistered(String phone) {
		return memberMapper.selectByPhone(phone) != null;
	}

	/** 로그인할 수 있는 회원 (탈퇴 회원 제외) */
	public Member findActiveByPhone(String phone) {
		return activeOnly(memberMapper.selectByPhone(phone));
	}

	public Member findActiveById(Long memberId) {
		return activeOnly(memberMapper.selectById(memberId));
	}

	/** 간편 로그인 계정에 연결된 회원 (탈퇴 회원 제외) */
	public Member findActiveBySocial(SocialAccount account) {
		return activeOnly(memberMapper.selectBySocial(account.loginType(), account.socialNo()));
	}

	/** 연결된 간편 로그인 (LOGIN_TYPE 순) */
	public List<Social> socials(Long memberId) {
		return memberMapper.selectSocials(memberId);
	}

	/** 본인인증을 마친 회원가입. 간편 로그인으로 시작했으면 social 도 함께 연결한다 */
	@Transactional
	public Member join(String name, String phone, LocalDateTime termsAgreedAt, SocialAccount social) {
		Member member = new Member();
		member.setMemberName(name);
		member.setPhone(phone);
		member.setIsVerified("Y");
		member.setTermsAgreedAt(termsAgreedAt);
		memberMapper.insertMember(member);
		if (social != null) {
			linkSocial(member.getMemberId(), social);
		}
		return member;
	}

	/** 기존 회원에 간편 로그인 연결 (같은 소셜 계정은 UNIQUE 로 한 회원에만) */
	@Transactional
	public void linkSocial(Long memberId, SocialAccount account) {
		Social social = new Social();
		social.setMemberId(memberId);
		social.setLoginType(account.loginType());
		social.setSocialNo(account.socialNo());
		memberMapper.insertSocial(social);
	}

	/**
	 * 회원 탈퇴: 간편 로그인 연결을 지우고 이름·번호를 파기한다.
	 * 견적·예약·후기는 MEMBER_ID 로 남고, 같은 번호로 다시 가입하면 새 회원이 된다.
	 */
	@Transactional
	public void withdraw(Long memberId) {
		memberMapper.deleteSocials(memberId);
		memberMapper.withdrawMember(memberId);
	}

	private static Member activeOnly(Member member) {
		return member != null && "Y".equals(member.getIsActive()) ? member : null;
	}
}
