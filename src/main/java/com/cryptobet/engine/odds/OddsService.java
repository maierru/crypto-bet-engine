package com.cryptobet.engine.odds;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OddsService {

    private static final BigDecimal BASE_PROBABILITY = new BigDecimal("0.5");
    private static final int SCALE = 4;

    private final BigDecimal vigRate;

    public OddsService(@Value("${betting.vig-rate:0.05}") BigDecimal vigRate) {
        this.vigRate = vigRate;
    }

    /**
     * Calculate decimal odds for a binary bet with vigorish.
     * Fair odds = 1 / baseProbability = 2.0
     * With vig: odds = 1 / (baseProbability * (1 + vigRate))
     */
    public BigDecimal calculateOdds() {
        BigDecimal impliedProbability = BASE_PROBABILITY.multiply(BigDecimal.ONE.add(vigRate));
        return BigDecimal.ONE.divide(impliedProbability, SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePotentialPayout(BigDecimal stake) {
        return stake.multiply(calculateOdds()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal getVigRate() {
        return vigRate;
    }
}
