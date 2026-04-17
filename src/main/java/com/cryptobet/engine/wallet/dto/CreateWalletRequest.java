package com.cryptobet.engine.wallet.dto;

import java.math.BigDecimal;

public record CreateWalletRequest(BigDecimal initialBalance) {
}
