package com.cryptobet.engine.bet;

import com.cryptobet.engine.bet.dto.BetResponse;
import com.cryptobet.engine.bet.dto.PlaceBetRequest;
import com.cryptobet.engine.odds.OddsService;
import com.cryptobet.engine.wallet.WalletNotFoundException;
import com.cryptobet.engine.wallet.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class BetService {

    private final BetRepository betRepository;
    private final WalletRepository walletRepository;
    private final OddsService oddsService;

    public BetService(BetRepository betRepository, WalletRepository walletRepository, OddsService oddsService) {
        this.betRepository = betRepository;
        this.walletRepository = walletRepository;
        this.oddsService = oddsService;
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
        bet = betRepository.save(bet);

        return BetResponse.from(bet);
    }

    @Transactional(readOnly = true)
    public BetResponse getBet(UUID id) {
        var bet = betRepository.findById(id)
                .orElseThrow(() -> new BetNotFoundException(id));
        return BetResponse.from(bet);
    }
}
