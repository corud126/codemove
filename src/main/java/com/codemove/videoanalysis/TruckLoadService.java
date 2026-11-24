package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codemove.videoanalysis.ItemStandardCatalog.Truck;

/**
 * 물품 부피 → 짐 톤수 (대표님 2026-11-23 통화, 팀 기준 EstimateCalculator 와 같은 방식).
 * 적재 부피 = 물품 부피 × 적재계수(2.0: 포장재 · 빈 공간 · 가구 속에서 꺼낸 짐 포함)
 * 톤 = 가장 큰 차(5톤 35 CBM)를 꽉 채우고, 남은 짐은 "1톤 2대"와 "들어가는 가장 작은 차 1대" 중 톤 합이 작은 쪽.
 * 예) 55 CBM → 5톤 + 남은 20 → 1톤 2대(2톤) < 2.5톤 → 5톤 + 1톤 + 1톤 = 7톤 (대표님 댁)
 * "톤"은 차가 아니라 짐 양이라 요금은 톤 합으로 낸다. 요금 계산은 아직 하지 않는다.
 */
@Service
public class TruckLoadService {

	private static final BigDecimal EPS = new BigDecimal("0.000001");

	private final BigDecimal loadFactor;

	/** label: "5톤 + 1톤 × 2" */
	public record Load(BigDecimal itemCbm, BigDecimal loadFactor, BigDecimal loadCbm, List<BigDecimal> trucks, BigDecimal ton,
			String label) {
	}

	public TruckLoadService(@Value("${codemove.video-analysis.load-factor:2.0}") BigDecimal loadFactor) {
		this.loadFactor = loadFactor;
	}

	public BigDecimal loadFactor() {
		return loadFactor;
	}

	/** 트럭 기준이 없으면 null */
	public Load estimate(BigDecimal itemCbm, List<Truck> trucks) {
		return estimate(itemCbm, trucks, loadFactor);
	}

	public static Load estimate(BigDecimal itemCbm, List<Truck> trucks, BigDecimal factor) {
		if (trucks == null || trucks.isEmpty() || itemCbm == null) {
			return null;
		}
		BigDecimal load = itemCbm.multiply(factor).setScale(2, RoundingMode.HALF_UP);
		List<BigDecimal> picked = pick(load, trucks);
		BigDecimal ton = picked.stream().reduce(BigDecimal.ZERO, BigDecimal::add).stripTrailingZeros();
		return new Load(itemCbm, factor, load, picked, ton.scale() < 0 ? ton.setScale(0) : ton, label(picked));
	}

	static List<BigDecimal> pick(BigDecimal load, List<Truck> trucks) {
		Truck small = trucks.get(0);
		Truck big = trucks.get(trucks.size() - 1);
		List<BigDecimal> out = new ArrayList<>();
		BigDecimal rest = load.max(BigDecimal.ZERO);
		while (rest.compareTo(big.loadCbm().add(EPS)) > 0) {
			out.add(big.ton());
			rest = rest.subtract(big.loadCbm());
		}
		if (rest.compareTo(EPS) <= 0 && !out.isEmpty()) {
			return out;
		}
		BigDecimal r = rest;
		Truck single = trucks.stream().filter(t -> t.loadCbm().add(EPS).compareTo(r) >= 0).findFirst().orElse(big);
		boolean twoSmall = r.compareTo(small.loadCbm().add(EPS)) > 0
				&& r.compareTo(small.loadCbm().multiply(BigDecimal.valueOf(2)).add(EPS)) <= 0
				&& small.ton().multiply(BigDecimal.valueOf(2)).compareTo(single.ton()) < 0;
		if (twoSmall) {
			out.add(small.ton());
			out.add(small.ton());
		} else {
			out.add(single.ton());
		}
		return out;
	}

	private static String label(List<BigDecimal> trucks) {
		Map<BigDecimal, Long> count = trucks.stream()
				.collect(Collectors.groupingBy(t -> t.stripTrailingZeros(), LinkedHashMap::new, Collectors.counting()));
		return count.entrySet().stream().map(e -> e.getKey().toPlainString() + "톤" + (e.getValue() > 1 ? " × " + e.getValue() : ""))
				.collect(Collectors.joining(" + "));
	}
}
