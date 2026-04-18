package com.cryptobet.engine.exposure;

import com.cryptobet.engine.exposure.dto.ExposureResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;

@RestController
@RequestMapping("/api/exposure")
public class ExposureController {

    private final ExposureService exposureService;

    public ExposureController(ExposureService exposureService) {
        this.exposureService = exposureService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ExposureResponse> getExposure(@PathVariable String symbol) {
        var exposure = exposureService.getExposure(symbol).setScale(2, RoundingMode.HALF_UP);
        return ResponseEntity.ok(new ExposureResponse(symbol, exposure));
    }
}
