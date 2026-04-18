package com.cryptobet.engine.price;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/prices")
@Profile("e2e")
public class PriceSeedController {

    private final PriceService priceService;

    public PriceSeedController(PriceService priceService) {
        this.priceService = priceService;
    }

    @PostMapping("/{symbol}")
    public ResponseEntity<Void> seedPrice(@PathVariable String symbol, @RequestBody Map<String, String> body) {
        var price = new BigDecimal(body.get("price"));
        priceService.updatePrice(symbol, price);
        return ResponseEntity.ok().build();
    }
}
