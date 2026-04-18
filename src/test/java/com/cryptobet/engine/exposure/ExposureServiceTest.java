package com.cryptobet.engine.exposure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExposureServiceTest {

    @Autowired
    private ExposureService exposureService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear all exposure keys before each test
        var keys = redisTemplate.keys("exposure:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void getExposure_noData_returnsZero() {
        BigDecimal exposure = exposureService.getExposure("BTCUSDT");
        assertThat(exposure).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void addExposure_incrementsCorrectly() {
        exposureService.addExposure("BTCUSDT", new BigDecimal("95.24"));

        BigDecimal exposure = exposureService.getExposure("BTCUSDT");
        assertThat(exposure).isEqualByComparingTo(new BigDecimal("95.24"));
    }

    @Test
    void addExposure_multipleAdds_accumulates() {
        exposureService.addExposure("ETHUSDT", new BigDecimal("50.00"));
        exposureService.addExposure("ETHUSDT", new BigDecimal("75.50"));

        BigDecimal exposure = exposureService.getExposure("ETHUSDT");
        assertThat(exposure).isEqualByComparingTo(new BigDecimal("125.50"));
    }

    @Test
    void removeExposure_decrementsCorrectly() {
        exposureService.addExposure("BTCUSDT", new BigDecimal("200.00"));
        exposureService.removeExposure("BTCUSDT", new BigDecimal("80.00"));

        BigDecimal exposure = exposureService.getExposure("BTCUSDT");
        assertThat(exposure).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    void exposure_isolatedPerSymbol() {
        exposureService.addExposure("BTCUSDT", new BigDecimal("100.00"));
        exposureService.addExposure("ETHUSDT", new BigDecimal("50.00"));

        assertThat(exposureService.getExposure("BTCUSDT")).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(exposureService.getExposure("ETHUSDT")).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void removeExposure_neverGoesBelowZero() {
        exposureService.addExposure("BTCUSDT", new BigDecimal("50.00"));
        exposureService.removeExposure("BTCUSDT", new BigDecimal("100.00"));

        BigDecimal exposure = exposureService.getExposure("BTCUSDT");
        assertThat(exposure).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
