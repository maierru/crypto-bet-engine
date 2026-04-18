package com.cryptobet.engine.settlement;

import com.cryptobet.engine.bet.*;
import com.cryptobet.engine.exposure.ExposureService;
import com.cryptobet.engine.price.PriceService;
import com.cryptobet.engine.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final BetRepository betRepository;
    private final WalletRepository walletRepository;
    private final PriceService priceService;
    private final ExposureService exposureService;

    public SettlementService(BetRepository betRepository, WalletRepository walletRepository,
                             PriceService priceService, ExposureService exposureService) {
        this.betRepository = betRepository;
        this.walletRepository = walletRepository;
        this.priceService = priceService;
        this.exposureService = exposureService;
    }

    @Transactional
    public boolean settleSingleBet(Bet bet) {
        var currentPrice = priceService.getPrice(bet.getSymbol());
        if (currentPrice.isEmpty()) {
            log.warn("No price available for {}, skipping bet {}", bet.getSymbol(), bet.getId());
            return false;
        }

        BigDecimal price = currentPrice.get();
        BetStatus outcome = determineOutcome(bet.getDirection(), bet.getEntryPrice(), price);

        bet.setStatus(outcome);
        bet.setPriceAtResolution(price);
        bet.setResolvedAt(Instant.now());

        switch (outcome) {
            case WON -> {
                var wallet = walletRepository.findByIdForUpdate(bet.getWalletId()).orElseThrow();
                wallet.deposit(bet.getPotentialPayout());
                walletRepository.save(wallet);
            }
            case PUSH -> {
                var wallet = walletRepository.findByIdForUpdate(bet.getWalletId()).orElseThrow();
                wallet.deposit(bet.getStake());
                walletRepository.save(wallet);
            }
            case LOST -> { /* stake already deducted, no action */ }
            default -> throw new IllegalStateException("Unexpected outcome: " + outcome);
        }

        betRepository.save(bet);
        exposureService.removeExposure(bet.getSymbol(), bet.getPotentialPayout());
        return true;
    }

    private BetStatus determineOutcome(BetDirection direction, BigDecimal entryPrice, BigDecimal currentPrice) {
        int comparison = currentPrice.compareTo(entryPrice);
        if (comparison == 0) {
            return BetStatus.PUSH;
        }
        return switch (direction) {
            case UP -> comparison > 0 ? BetStatus.WON : BetStatus.LOST;
            case DOWN -> comparison < 0 ? BetStatus.WON : BetStatus.LOST;
        };
    }
}
