package com.cryptobet.engine.exposure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ExposureService {

    private static final String KEY_PREFIX = "exposure:";

    private final StringRedisTemplate redisTemplate;

    public ExposureService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addExposure(String symbol, BigDecimal amount) {
        String key = KEY_PREFIX + symbol;
        String current = redisTemplate.opsForValue().get(key);
        BigDecimal currentVal = current != null ? new BigDecimal(current) : BigDecimal.ZERO;
        BigDecimal newVal = currentVal.add(amount).setScale(4, RoundingMode.HALF_UP);
        redisTemplate.opsForValue().set(key, newVal.toPlainString());
    }

    public void removeExposure(String symbol, BigDecimal amount) {
        String key = KEY_PREFIX + symbol;
        String current = redisTemplate.opsForValue().get(key);
        BigDecimal currentVal = current != null ? new BigDecimal(current) : BigDecimal.ZERO;
        BigDecimal newVal = currentVal.subtract(amount);
        if (newVal.compareTo(BigDecimal.ZERO) < 0) {
            newVal = BigDecimal.ZERO;
        }
        redisTemplate.opsForValue().set(key, newVal.setScale(4, RoundingMode.HALF_UP).toPlainString());
    }

    public BigDecimal getExposure(String symbol) {
        String key = KEY_PREFIX + symbol;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? new BigDecimal(value) : BigDecimal.ZERO;
    }
}
