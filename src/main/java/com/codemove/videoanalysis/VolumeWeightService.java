package com.codemove.videoanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.codemove.videoanalysis.ItemMappingService.MappedItem;

/**
 * 표준 규격 × 수량으로 부피 · 무게를 계산한다 (AI 응답과 분리, 같은 입력이면 같은 결과).
 * 반올림: 품목별 합계를 부피 소수 셋째 · 무게 소수 첫째 자리에서 반올림하고, 전체 합계는 반올림된 품목 합계를 더한다 (화면 합계와 일치).
 * 기준 부피는 외접 직육면체 기준 추정값이며 포장 · 적재율 · 분해 같은 견적 요인은 기존 견적 로직에서 따로 반영한다.
 */
@Service
public class VolumeWeightService {

	public static final int CBM_SCALE = 3;
	public static final int KG_SCALE = 1;

	/** 품목 한 줄의 계산 결과. 규격이 없으면 cbm/kg 은 null */
	public record Line(MappedItem item, BigDecimal unitCbm, BigDecimal totalCbm, BigDecimal unitKg, BigDecimal totalKg) {
	}

	/**
	 * lines: 품목별 · totalCbm/totalKg: 계산된 품목만 더한 합계
	 * cbmMissing: 규격이 없어 부피를 못 낸 줄 수 · kgMissing: 무게 기준이 없어 무게를 못 낸 줄 수 (부피는 있음)
	 */
	public record Result(List<Line> lines, BigDecimal totalCbm, BigDecimal totalKg, int cbmMissing, int kgMissing,
			int reviewCount) {
	}

	public Result calculate(List<MappedItem> items) {
		List<Line> lines = new ArrayList<>();
		BigDecimal cbm = BigDecimal.ZERO.setScale(CBM_SCALE);
		BigDecimal kg = BigDecimal.ZERO.setScale(KG_SCALE);
		int cbmMissing = 0;
		int kgMissing = 0;
		int review = 0;
		for (MappedItem m : items) {
			if (m.needsReview()) {
				review++;
			}
			ItemStandard s = m.standard();
			int quantity = m.ai().quantity();
			if (s == null || quantity <= 0) {
				cbmMissing++;
				lines.add(new Line(m, null, null, null, null));
				continue;
			}
			BigDecimal lineCbm = s.cbm().multiply(BigDecimal.valueOf(quantity)).setScale(CBM_SCALE, RoundingMode.HALF_UP);
			cbm = cbm.add(lineCbm);
			BigDecimal lineKg = null;
			if (s.weightKg() == null) {
				kgMissing++;
			} else {
				lineKg = s.weightKg().multiply(BigDecimal.valueOf(quantity)).setScale(KG_SCALE, RoundingMode.HALF_UP);
				kg = kg.add(lineKg);
			}
			lines.add(new Line(m, s.cbm(), lineCbm, s.weightKg(), lineKg));
		}
		return new Result(List.copyOf(lines), cbm, kg, cbmMissing, kgMissing, review);
	}
}
