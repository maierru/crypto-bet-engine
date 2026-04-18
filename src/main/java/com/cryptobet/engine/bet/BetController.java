package com.cryptobet.engine.bet;

import com.cryptobet.engine.bet.dto.BetResponse;
import com.cryptobet.engine.bet.dto.PlaceBetRequest;
import jakarta.validation.Valid;
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
    public ResponseEntity<BetResponse> placeBet(@Valid @RequestBody PlaceBetRequest request) {
        var response = betService.placeBet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BetResponse> getBet(@PathVariable UUID id) {
        var response = betService.getBet(id);
        return ResponseEntity.ok(response);
    }
}
