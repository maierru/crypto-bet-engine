package com.cryptobet.engine.bet;

import com.cryptobet.engine.bet.dto.BetResponse;
import com.cryptobet.engine.bet.dto.PlaceBetRequest;
import com.cryptobet.engine.wallet.WalletNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BetService betService;

    public BetController(BetService betService) {
        this.betService = betService;
    }

    @PostMapping
    public ResponseEntity<BetResponse> placeBet(@RequestBody PlaceBetRequest request) {
        var response = betService.placeBet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BetResponse> getBet(@PathVariable UUID id) {
        var response = betService.getBet(id);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(BetNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(BetNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Void> handleWalletNotFound(WalletNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().build();
    }
}
