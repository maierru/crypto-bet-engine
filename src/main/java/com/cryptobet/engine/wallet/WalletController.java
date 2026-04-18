package com.cryptobet.engine.wallet;

import com.cryptobet.engine.bet.BetService;
import com.cryptobet.engine.bet.BetStatus;
import com.cryptobet.engine.bet.dto.BetResponse;
import com.cryptobet.engine.wallet.dto.CreateWalletRequest;
import com.cryptobet.engine.wallet.dto.DepositRequest;
import com.cryptobet.engine.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;
    private final BetService betService;

    public WalletController(WalletService walletService, BetService betService) {
        this.walletService = walletService;
        this.betService = betService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        var response = walletService.createWallet(request.initialBalance(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
        var response = walletService.getWallet(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<WalletResponse> deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        var response = walletService.deposit(id, request.amount());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/bets")
    public ResponseEntity<Page<BetResponse>> getBetsForWallet(
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BetStatus betStatus = status != null ? BetStatus.valueOf(status) : null;
        var bets = betService.getBetsForWallet(id, betStatus, PageRequest.of(page, size));
        return ResponseEntity.ok(bets);
    }
}
