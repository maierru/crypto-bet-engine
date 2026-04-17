package com.cryptobet.engine.wallet.dto;

import com.cryptobet.engine.wallet.Wallet;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal balance,
        String currency
) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance().setScale(2), wallet.getCurrency());
    }
}
