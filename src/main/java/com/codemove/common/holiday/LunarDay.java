package com.codemove.common.holiday;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 음력 날짜(일)만 계산 — 손 없는 날(음력 끝자리 9 · 0일) 표시용.
 * 한국 시각(UTC+9) 기준 합삭(신월) 날짜를 음력 1일로 본다. 합삭 시각은 Meeus 「Astronomical Algorithms」 49장 식(오차 약 1분).
 * 손 없는 날은 표시만 하고 휴무가 아니라서 근사 계산으로 충분하다.
 */
final class LunarDay {

	private static final double SYNODIC = 29.530588861;
	/** 2000-01-06 합삭 (JDE) */
	private static final double JDE_2000 = 2451550.09766;
	/** 1970-01-01 00:00 UTC 의 율리우스일 */
	private static final double JD_EPOCH = 2440587.5;
	/** TT − UT (약 70초) */
	private static final double DELTA_T_DAYS = 70.0 / 86400;

	private LunarDay() {
	}

	/** 음력 일 (1~30) */
	static int of(LocalDate date) {
		long target = date.toEpochDay();
		// 그날 정오 무렵의 k 추정 → 앞뒤로 맞춰 그날 이전(포함) 가장 가까운 합삭 날짜를 찾는다
		double jd = JD_EPOCH + target + 0.5;
		int k = (int) Math.floor((jd - JDE_2000) / SYNODIC) + 1;
		long nm = newMoonKstDay(k);
		while (nm > target) {
			nm = newMoonKstDay(--k);
		}
		long next = newMoonKstDay(k + 1);
		while (next <= target) {
			nm = next;
			next = newMoonKstDay(++k + 1);
		}
		return (int) (target - nm) + 1;
	}

	/** 손 없는 날: 음력 9 · 10 · 19 · 20 · 29 · 30일 */
	static boolean isNoMoveDay(LocalDate date) {
		int d = of(date);
		return d % 10 == 9 || d % 10 == 0;
	}

	/** k 번째 합삭의 한국 날짜 (epoch day) */
	private static long newMoonKstDay(int k) {
		double jdUt = newMoonJde(k) - DELTA_T_DAYS;
		double epochSeconds = (jdUt - JD_EPOCH) * 86400;
		LocalDateTime kst = LocalDateTime.ofEpochSecond((long) Math.floor(epochSeconds), 0, ZoneOffset.ofHours(9));
		return kst.toLocalDate().toEpochDay();
	}

	private static double newMoonJde(int k) {
		double t = k / 1236.85;
		double t2 = t * t, t3 = t2 * t, t4 = t3 * t;
		double jde = JDE_2000 + SYNODIC * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4;
		double e = 1 - 0.002516 * t - 0.0000074 * t2;
		double m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3);
		double mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4);
		double f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4);
		double om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3);
		jde += -0.40720 * Math.sin(mp)
				+ 0.17241 * e * Math.sin(m)
				+ 0.01608 * Math.sin(2 * mp)
				+ 0.01039 * Math.sin(2 * f)
				+ 0.00739 * e * Math.sin(mp - m)
				- 0.00514 * e * Math.sin(mp + m)
				+ 0.00208 * e * e * Math.sin(2 * m)
				- 0.00111 * Math.sin(mp - 2 * f)
				- 0.00057 * Math.sin(mp + 2 * f)
				+ 0.00056 * e * Math.sin(2 * mp + m)
				- 0.00042 * Math.sin(3 * mp)
				+ 0.00042 * e * Math.sin(m + 2 * f)
				+ 0.00038 * e * Math.sin(m - 2 * f)
				- 0.00024 * e * Math.sin(2 * mp - m)
				- 0.00017 * Math.sin(om)
				- 0.00007 * Math.sin(mp + 2 * m)
				+ 0.00004 * Math.sin(2 * mp - 2 * f)
				+ 0.00004 * Math.sin(3 * m)
				+ 0.00003 * Math.sin(mp + m - 2 * f)
				+ 0.00003 * Math.sin(2 * mp + 2 * f)
				- 0.00003 * Math.sin(mp + m + 2 * f)
				+ 0.00003 * Math.sin(mp - m + 2 * f)
				- 0.00002 * Math.sin(mp - m - 2 * f)
				- 0.00002 * Math.sin(3 * mp + m)
				+ 0.00002 * Math.sin(4 * mp);
		return jde;
	}

	private static double rad(double deg) {
		return Math.toRadians(deg % 360);
	}
}
