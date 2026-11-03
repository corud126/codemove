package com.codemove.common.sms;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 휴대폰 인증번호 발송·확인. 인증번호는 DB 에 두지 않고 세션에 3분간 보관한다 (ERD 수정안 TBL_MEMBER).
 * purpose 별로 따로 보관해 회원가입·로그인 인증이 섞이지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class PhoneVerifier {

	public static final Duration TTL = Duration.ofMinutes(3);
	private static final int MAX_FAILS = 5;
	private static final String KEY_PREFIX = "phoneVerify.";

	private final SmsSender smsSender;
	private final SecureRandom random = new SecureRandom();

	public enum Result {
		OK, MISMATCH, EXPIRED
	}

	/** 새 인증번호를 보내고 이전 번호는 무효로 한다 */
	public void send(HttpSession session, String purpose, String phone) {
		String code = String.format("%06d", random.nextInt(1_000_000));
		session.setAttribute(KEY_PREFIX + purpose, new Pending(phone, code, Instant.now().plus(TTL)));
		smsSender.send(phone, "[명품익스프레스] 인증번호 [" + code + "]를 입력해 주세요.");
	}

	/** 남은 시간(초). 보낸 적 없거나 만료면 0 */
	public long remainingSeconds(HttpSession session, String purpose) {
		Pending pending = (Pending) session.getAttribute(KEY_PREFIX + purpose);
		if (pending == null) {
			return 0;
		}
		return Math.max(0, Duration.between(Instant.now(), pending.expiresAt()).toSeconds());
	}

	/** 확인에 성공하면 인증번호를 지운다. 5번 틀리면 만료 처리 */
	public Result verify(HttpSession session, String purpose, String phone, String code) {
		String key = KEY_PREFIX + purpose;
		Pending pending = (Pending) session.getAttribute(key);
		if (pending == null || !pending.phone().equals(phone) || Instant.now().isAfter(pending.expiresAt())) {
			session.removeAttribute(key);
			return Result.EXPIRED;
		}
		if (!pending.code().equals(code)) {
			Pending failed = pending.failed();
			if (failed.fails() >= MAX_FAILS) {
				session.removeAttribute(key);
				return Result.EXPIRED;
			}
			session.setAttribute(key, failed);
			return Result.MISMATCH;
		}
		session.removeAttribute(key);
		return Result.OK;
	}

	record Pending(String phone, String code, Instant expiresAt, int fails) implements Serializable {

		Pending(String phone, String code, Instant expiresAt) {
			this(phone, code, expiresAt, 0);
		}

		Pending failed() {
			return new Pending(phone, code, expiresAt, fails + 1);
		}
	}
}
