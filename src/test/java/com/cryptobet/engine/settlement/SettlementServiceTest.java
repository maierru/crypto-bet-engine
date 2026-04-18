package com.cryptobet.engine.settlement;

import com.cryptobet.engine.bet.*;
import com.cryptobet.engine.exposure.ExposureService;
import com.cryptobet.engine.price.PriceService;
import com.cryptobet.engine.wallet.Wallet;
import com.cryptobet.engine.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementServiceTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementScheduler settlementScheduler;

    @Autowired
    private BetRepository betRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PriceService priceService;

    @Autowired
    private ExposureService exposureService;

    private Wallet savedWallet;

    @BeforeEach
    void setUp() {
        betRepository.deleteAll();
        walletRepository.deleteAll();
    }

    private Wallet createWallet(BigDecimal balance) {
        return walletRepository.save(new Wallet(balance));
    }

    private Bet createExpiredBet(UUID walletId, BetDirection direction, BigDecimal stake, BigDecimal entryPrice) {
        var bet = new Bet(walletId, "BTCUSDT", direction, stake, entryPrice);
        bet.setOdds(new BigDecimal("1.9048"));
        bet.setPotentialPayout(stake.multiply(new BigDecimal("1.9048")).setScale(4, RoundingMode.HALF_UP));
        bet.setResolveAt(Instant.now().minusSeconds(10));
        return betRepository.save(bet);
    }

    @Test
    void settlesBet_won_upDirection_priceIncreased() {
        // Wallet with 900 (simulates 1000 - 100 stake already deducted)
        var wallet = createWallet(new BigDecimal("900.00"));
        var bet = createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("61000.00"));
        exposureService.addExposure("BTCUSDT", bet.getPotentialPayout());

        settlementService.settleSingleBet(bet);

        var settled = betRepository.findById(bet.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(settled.getPriceAtResolution()).isEqualByComparingTo(new BigDecimal("61000.00"));
        assertThat(settled.getResolvedAt()).isNotNull();

        // Wallet: 900 + 190.48 payout = 1090.48
        var updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1090.4800"));
    }

    @Test
    void settlesBet_won_downDirection_priceDecreased() {
        var wallet = createWallet(new BigDecimal("900.00"));
        var bet = createExpiredBet(wallet.getId(), BetDirection.DOWN, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("59000.00"));
        exposureService.addExposure("BTCUSDT", bet.getPotentialPayout());

        settlementService.settleSingleBet(bet);

        var settled = betRepository.findById(bet.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(BetStatus.WON);

        var updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1090.4800"));
    }

    @Test
    void settlesBet_lost_upDirection_priceDecreased() {
        var wallet = createWallet(new BigDecimal("900.00"));
        var bet = createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("59000.00"));
        exposureService.addExposure("BTCUSDT", bet.getPotentialPayout());

        settlementService.settleSingleBet(bet);

        var settled = betRepository.findById(bet.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(settled.getPriceAtResolution()).isEqualByComparingTo(new BigDecimal("59000.00"));
        assertThat(settled.getResolvedAt()).isNotNull();

        // Wallet unchanged — stake already deducted
        var updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("900.0000"));
    }

    @Test
    void settlesBet_push_priceUnchanged() {
        var wallet = createWallet(new BigDecimal("900.00"));
        var bet = createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("60000.00"));
        exposureService.addExposure("BTCUSDT", bet.getPotentialPayout());

        settlementService.settleSingleBet(bet);

        var settled = betRepository.findById(bet.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(BetStatus.PUSH);

        // Wallet gets stake refunded: 900 + 100 = 1000
        var updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1000.0000"));
    }

    @Test
    void settlesBet_recordsPriceAtResolutionAndResolvedAt() {
        var wallet = createWallet(new BigDecimal("950.00"));
        var bet = createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("50.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("61000.00"));
        exposureService.addExposure("BTCUSDT", bet.getPotentialPayout());

        var beforeSettlement = Instant.now();
        settlementService.settleSingleBet(bet);

        var settled = betRepository.findById(bet.getId()).orElseThrow();
        assertThat(settled.getPriceAtResolution()).isEqualByComparingTo(new BigDecimal("61000.00"));
        assertThat(settled.getResolvedAt()).isAfterOrEqualTo(beforeSettlement);
    }

    @Test
    void onlySettlesBets_withResolveAtInPast() {
        var wallet = createWallet(new BigDecimal("800.00"));

        // Future bet — should NOT be settled
        var futureBet = new Bet(wallet.getId(), "BTCUSDT", BetDirection.UP, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        futureBet.setOdds(new BigDecimal("1.9048"));
        futureBet.setPotentialPayout(new BigDecimal("190.4800"));
        futureBet.setResolveAt(Instant.now().plusSeconds(3600));
        futureBet = betRepository.save(futureBet);

        // Past bet — should be settled
        var pastBet = createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("61000.00"));

        settlementScheduler.settleBets();

        var futureResult = betRepository.findById(futureBet.getId()).orElseThrow();
        assertThat(futureResult.getStatus()).isEqualTo(BetStatus.OPEN);

        var pastResult = betRepository.findById(pastBet.getId()).orElseThrow();
        assertThat(pastResult.getStatus()).isEqualTo(BetStatus.WON);
    }

    @Test
    void decrementsRedisExposure_afterSettlement() {
        var wallet = createWallet(new BigDecimal("900.00"));
        var bet = createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("100.00"), new BigDecimal("60000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("61000.00"));
        exposureService.addExposure("BTCUSDT", bet.getPotentialPayout());

        var exposureBefore = exposureService.getExposure("BTCUSDT");
        settlementService.settleSingleBet(bet);
        var exposureAfter = exposureService.getExposure("BTCUSDT");

        assertThat(exposureAfter).isLessThan(exposureBefore);
        assertThat(exposureAfter).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void batchLimit_settlesMaximum100BetsPerCycle() {
        var wallet = createWallet(new BigDecimal("10000.00"));
        priceService.updatePrice("BTCUSDT", new BigDecimal("61000.00"));

        // Create 105 expired bets
        for (int i = 0; i < 105; i++) {
            createExpiredBet(wallet.getId(), BetDirection.UP, new BigDecimal("1.00"), new BigDecimal("60000.00"));
        }

        settlementScheduler.settleBets();

        long settledCount = betRepository.findAll().stream()
                .filter(b -> b.getStatus() != BetStatus.OPEN)
                .count();
        assertThat(settledCount).isEqualTo(100);

        long openCount = betRepository.findAll().stream()
                .filter(b -> b.getStatus() == BetStatus.OPEN)
                .count();
        assertThat(openCount).isEqualTo(5);
    }
}
