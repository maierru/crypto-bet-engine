package com.cryptobet.engine.exposure.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record ExposureResponse(
        String symbol,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalExposure
) {
}
