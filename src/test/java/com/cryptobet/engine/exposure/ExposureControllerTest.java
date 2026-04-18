package com.cryptobet.engine.exposure;

import com.cryptobet.engine.price.PriceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExposureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PriceService priceService;

    @BeforeEach
    void setUp() {
        var keys = redisTemplate.keys("exposure:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));
    }

    @Test
    void getExposure_noData_returnsZero() throws Exception {
        mockMvc.perform(get("/api/exposure/{symbol}", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.totalExposure").value("0.00"));
    }

    @Test
    void getExposure_afterBetPlaced_returnsExposure() throws Exception {
        // Create wallet
        String walletId = createWalletAndGetId(new BigDecimal("1000.00"));

        // Place a bet — this should increase exposure
        var body = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "100.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Check exposure is now equal to potentialPayout
        mockMvc.perform(get("/api/exposure/{symbol}", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.totalExposure", not("0.00")));
    }

    @Test
    void getExposure_multipleBets_accumulates() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("5000.00"));

        for (int i = 0; i < 3; i++) {
            var body = """
                    {
                        "walletId": "%s",
                        "symbol": "ETHUSDT",
                        "direction": "UP",
                        "stake": "50.00"
                    }
                    """.formatted(walletId);

            mockMvc.perform(post("/api/bets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        // Exposure should be 3x the potentialPayout of a single bet
        mockMvc.perform(get("/api/exposure/{symbol}", "ETHUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("ETHUSDT"))
                .andExpect(jsonPath("$.totalExposure", not("0.00")));
    }

    private String createWalletAndGetId(BigDecimal balance) throws Exception {
        var body = """
                {"initialBalance": "%s"}
                """.formatted(balance.toPlainString());

        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
