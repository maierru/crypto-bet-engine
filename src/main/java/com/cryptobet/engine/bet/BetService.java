package com.cryptobet.engine.bet;

import com.cryptobet.engine.bet.dto.BetResponse;
import com.cryptobet.engine.bet.dto.PlaceBetRequest;
import com.cryptobet.engine.exposure.ExposureService;
import com.cryptobet.engine.odds.OddsService;
import com.cryptobet.engine.wallet.WalletNotFoundException;
import com.cryptobet.engine.wallet.WalletRepository;
import com.cryptobet.engine.websocket.BetUpdateEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class BetService {

    private final BetRepository betRepository;
    private final WalletRepository walletRepository;
    private final OddsService oddsService;
    private final ExposureService exposureService;
    private final ApplicationEventPublisher eventPublisher;
    private final int defaultDurationSeconds;

    public BetService(BetRepository betRepository, WalletRepository walletRepository,
                      OddsService oddsService, ExposureService exposureService,
                      ApplicationEventPublisher eventPublisher,
                      @Value("${betting.default-duration-seconds:60}") int defaultDurationSeconds) {
        this.betRepository = betRepository;
        this.walletRepository = walletRepository;
        this.oddsService = oddsService;
        this.exposureService = exposureService;
        this.eventPublisher = eventPublisher;
        this.defaultDurationSeconds = defaultDurationSeconds;
    }

    @Transactional
    public BetResponse placeBet(PlaceBetRequest request) {
        if (request.stake().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Stake must be positive");
        }

        var direction = BetDirection.valueOf(request.direction());

        var wallet = walletRepository.findByIdForUpdate(request.walletId())
                .orElseThrow(() -> new WalletNotFoundException(request.walletId()));

        wallet.deductStake(request.stake());
        walletRepository.save(wallet);

        var bet = new Bet(request.walletId(), request.symbol(), direction, request.stake(), request.entryPrice());
        bet.setOdds(oddsService.calculateOdds());
        bet.setPotentialPayout(oddsService.calculatePotentialPayout(request.stake()));
        int duration = request.durationSeconds() != null ? request.durationSeconds() : defaultDurationSeconds;
        bet.setResolveAt(Instant.now().plusSeconds(duration));
        bet = betRepository.save(bet);

        exposureService.addExposure(bet.getSymbol(), bet.getPotentialPayout());

        eventPublisher.publishEvent(new BetUpdateEvent(
                bet.getId(), bet.getWalletId(), BetStatus.OPEN, BigDecimal.ZERO));

        return BetResponse.from(bet);
    }

    @Transactional(readOnly = true)
    public BetResponse getBet(UUID id) {
        var bet = betRepository.findById(id)
                .orElseThrow(() -> new BetNotFoundException(id));
        return BetResponse.from(bet);
    }

    @Transactional(readOnly = true)
    public Page<BetResponse> getBetsForWallet(UUID walletId, BetStatus status, Pageable pageable) {
        Page<Bet> bets;
        if (status != null) {
            bets = betRepository.findByWalletIdAndStatusOrderByCreatedAtDesc(walletId, status, pageable);
        } else {
            bets = betRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
        }
        return bets.map(BetResponse::from);
    }
}
