package com.codemove.member;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.domain.Member;
import com.codemove.domain.Social;
import com.codemove.notification.NotificationDetail;
import com.codemove.notification.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberMapper memberMapper;
	private final NotificationService notificationService;

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
		notificationService.notifyAdmins(member.getMemberId(), NotificationDetail.MEMBER_JOINED.code(), "신규 가입",
				name + " 고객님이 회원으로 가입했습니다.", "/admin/members");
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

	/**
	 * 휴대폰 번호 변경 (본인인증을 마친 새 번호, 010-0000-0000 형식).
	 * 다른 회원이 쓰는 번호면 IllegalStateException(화면 문구), 탈퇴 등으로 회원이 없으면 null. 바뀐 회원
	 */
	@Transactional
	public Member changePhone(Long memberId, String phone) {
		Member owner = memberMapper.selectByPhone(phone);
		if (owner != null && !owner.getMemberId().equals(memberId)) {
			throw new IllegalStateException("이미 가입된 번호예요. 다른 번호를 입력해 주세요.");
		}
		try {
			if (memberMapper.updatePhone(memberId, phone) == 0) {
				return null;
			}
		} catch (DuplicateKeyException e) {
			// 확인과 변경 사이에 같은 번호로 가입된 경우
			throw new IllegalStateException("이미 가입된 번호예요. 다른 번호를 입력해 주세요.");
		}
		// 번호 변경 알림: 공통코드 NTF_DETAIL 에 해당 종류가 없어 보내지 않음 (필요하면 코드 추가)
		return findActiveById(memberId);
	}

	private static Member activeOnly(Member member) {
		return member != null && "Y".equals(member.getIsActive()) ? member : null;
	}
}
