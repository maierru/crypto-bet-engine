package com.cryptobet.engine.wallet;

import com.cryptobet.engine.wallet.dto.CreateWalletRequest;
import com.cryptobet.engine.wallet.dto.DepositRequest;
import com.cryptobet.engine.wallet.dto.WalletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestBody CreateWalletRequest request) {
        var response = walletService.createWallet(request.initialBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
        var response = walletService.getWallet(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<WalletResponse> deposit(@PathVariable UUID id, @RequestBody DepositRequest request) {
        var response = walletService.deposit(id, request.amount());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(WalletNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().build();
    }
}
