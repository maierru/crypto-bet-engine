package com.cryptobet.engine.price;

import com.cryptobet.engine.price.dto.PriceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PriceResponse> getPrice(@PathVariable String symbol) {
        return priceService.getPrice(symbol)
                .map(price -> ResponseEntity.ok(PriceResponse.from(symbol, price)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<PriceResponse> getAllPrices() {
        return priceService.getAllPrices().entrySet().stream()
                .map(e -> PriceResponse.from(e.getKey(), e.getValue()))
                .toList();
    }
}
