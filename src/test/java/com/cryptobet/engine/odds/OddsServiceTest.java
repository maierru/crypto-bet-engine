package com.cryptobet.engine.odds;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class OddsServiceTest {

    private final OddsService oddsService = new OddsService(new BigDecimal("0.05"));

    @Test
    void calculateOdds_returnsCorrectOddsWithVigorish() {
        // Fair odds for 50/50 = 2.0, with 5% vig: 1 / (0.5 * 1.05) = 1.9048
        BigDecimal odds = oddsService.calculateOdds();
        assertEquals(new BigDecimal("1.9048"), odds);
    }

    @Test
    void calculateOdds_withZeroVig_returnsFairOdds() {
        var fairService = new OddsService(BigDecimal.ZERO);
        BigDecimal odds = fairService.calculateOdds();
        assertEquals(new BigDecimal("2.0000"), odds);
    }

    @Test
    void calculateOdds_withHighVig_returnsLowerOdds() {
        var highVigService = new OddsService(new BigDecimal("0.10"));
        BigDecimal odds = highVigService.calculateOdds();
        // 1 / (0.5 * 1.10) = 1 / 0.55 = 1.8182
        assertEquals(new BigDecimal("1.8182"), odds);
    }

    @Test
    void calculatePotentialPayout_returnsStakeTimesOdds() {
        BigDecimal stake = new BigDecimal("100.00");
        BigDecimal payout = oddsService.calculatePotentialPayout(stake);
        // 100 * 1.9048 = 190.4800
        assertEquals(new BigDecimal("190.4800"), payout);
    }

    @Test
    void calculatePotentialPayout_smallStake() {
        BigDecimal stake = new BigDecimal("1.00");
        BigDecimal payout = oddsService.calculatePotentialPayout(stake);
        assertEquals(new BigDecimal("1.9048"), payout);
    }

    @Test
    void getVigRate_returnsConfiguredRate() {
        assertEquals(new BigDecimal("0.05"), oddsService.getVigRate());
    }
}
