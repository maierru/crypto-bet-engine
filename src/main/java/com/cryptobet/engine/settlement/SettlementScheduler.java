package com.cryptobet.engine.settlement;

import com.cryptobet.engine.bet.Bet;
import com.cryptobet.engine.bet.BetRepository;
import com.cryptobet.engine.bet.BetStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final BetRepository betRepository;
    private final SettlementService settlementService;
    private final boolean schedulingEnabled;

    public SettlementScheduler(
            BetRepository betRepository,
            SettlementService settlementService,
            @Value("${settlement.scheduling-enabled:true}") boolean schedulingEnabled) {
        this.betRepository = betRepository;
        this.settlementService = settlementService;
        this.schedulingEnabled = schedulingEnabled;
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduledSettle() {
        if (!schedulingEnabled) return;
        settleBets();
    }

    public void settleBets() {
        List<Bet> expiredBets = betRepository.findSettleableBets(
                BetStatus.OPEN, Instant.now(), PageRequest.of(0, BATCH_SIZE));

        int settled = 0;
        for (Bet bet : expiredBets) {
            try {
                if (settlementService.settleSingleBet(bet)) {
                    settled++;
                }
            } catch (Exception e) {
                log.error("Failed to settle bet {}: {}", bet.getId(), e.getMessage());
            }
        }

        if (settled > 0) {
            log.info("Settled {} bets", settled);
        }
    }
}
