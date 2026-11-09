package com.codemove.request;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** 예상 금액 · 톤 환산 (설정값: codemove.quote) */
@Component
@EnableConfigurationProperties(QuoteProperties.class)
public class PriceCalculator {

	/** PACK_TYPE 02 반포장 */
	private static final String PARTIAL_PACK = "02";

	private final QuoteProperties props;

	public PriceCalculator(QuoteProperties props) {
		this.props = props;
	}

	public record Estimate(long amount, long min, long max) {
	}

	/** CBM ÷ cbmPerTon, tonStep 단위 올림, 최소 minTon */
	public BigDecimal ton(BigDecimal cbm) {
		BigDecimal steps = cbm.divide(props.cbmPerTon(), 6, RoundingMode.HALF_UP)
				.divide(props.tonStep(), 0, RoundingMode.CEILING);
		return steps.multiply(props.tonStep()).max(props.minTon()).setScale(1, RoundingMode.HALF_UP);
	}

	public Estimate estimate(BigDecimal cbm, String packType) {
		QuoteProperties.Pricing p = props.pricing();
		BigDecimal base = BigDecimal.valueOf(p.baseFee()).add(BigDecimal.valueOf(p.perTon()).multiply(ton(cbm)));
		if (PARTIAL_PACK.equals(packType)) {
			base = base.multiply(p.partialPackRate());
		}
		return new Estimate(round(base), round(base.multiply(p.rangeMinRate())), round(base.multiply(p.rangeMaxRate())));
	}

	/** 날짜 미정 견적서 초안의 이사비용: (최소 + 최대) / 2 */
	public long movingCost(Estimate estimate, boolean dateKnown) {
		return dateKnown ? estimate.amount() : roundUnit((estimate.min() + estimate.max()) / 2);
	}

	public long deposit(long total) {
		return BigDecimal.valueOf(total).multiply(props.depositRate()).setScale(0, RoundingMode.HALF_UP).longValue();
	}

	private long round(BigDecimal value) {
		long unit = props.pricing().roundUnit();
		return value.divide(BigDecimal.valueOf(unit), 0, RoundingMode.HALF_UP).longValue() * unit;
	}

	private long roundUnit(long value) {
		return round(BigDecimal.valueOf(value));
	}
}
