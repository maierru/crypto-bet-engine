package com.cryptobet.engine.wallet;

import com.cryptobet.engine.wallet.dto.WalletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public WalletResponse createWallet(BigDecimal initialBalance) {
        var wallet = new Wallet(initialBalance);
        wallet = walletRepository.save(wallet);
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID id) {
        var wallet = walletRepository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(id));
        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletResponse deposit(UUID id, BigDecimal amount) {
        var wallet = walletRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new WalletNotFoundException(id));
        wallet.deposit(amount);
        wallet = walletRepository.save(wallet);
        return WalletResponse.from(wallet);
    }
}
