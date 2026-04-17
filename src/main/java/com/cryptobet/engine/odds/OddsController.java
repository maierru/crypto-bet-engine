package com.cryptobet.engine.odds;

import com.cryptobet.engine.bet.BetDirection;
import com.cryptobet.engine.odds.dto.OddsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/odds")
public class OddsController {

    private final OddsService oddsService;

    public OddsController(OddsService oddsService) {
        this.oddsService = oddsService;
    }

    @GetMapping
    public ResponseEntity<OddsResponse> getOdds(
            @RequestParam String symbol,
            @RequestParam String direction) {
        var dir = BetDirection.valueOf(direction);
        var odds = oddsService.calculateOdds();
        var response = new OddsResponse(symbol, dir.name(), odds, oddsService.getVigRate());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().build();
    }
}
