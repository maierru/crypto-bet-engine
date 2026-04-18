package com.cryptobet.engine.odds;

import com.cryptobet.engine.price.PriceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class OddsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PriceService priceService;

    @BeforeEach
    void seedPrices() {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));
    }

    @Test
    void getOdds_returnsCurrentOddsWithVig() throws Exception {
        mockMvc.perform(get("/api/odds")
                        .param("symbol", "BTCUSDT")
                        .param("direction", "UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.direction").value("UP"))
                .andExpect(jsonPath("$.odds").value("1.9048"))
                .andExpect(jsonPath("$.vigRate").value("0.05"));
    }

    @Test
    void getOdds_downDirection_returnsSameOdds() throws Exception {
        // Binary bet: both directions have same odds
        mockMvc.perform(get("/api/odds")
                        .param("symbol", "ETHUSDT")
                        .param("direction", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("ETHUSDT"))
                .andExpect(jsonPath("$.direction").value("DOWN"))
                .andExpect(jsonPath("$.odds").value("1.9048"));
    }

    @Test
    void getOdds_invalidDirection_returns400() throws Exception {
        mockMvc.perform(get("/api/odds")
                        .param("symbol", "BTCUSDT")
                        .param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeBet_includesOddsAndPotentialPayout() throws Exception {
        // Create wallet
        var walletBody = """
                {"initialBalance": "500.00"}
                """;
        MvcResult walletResult = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(walletBody))
                .andExpect(status().isCreated())
                .andReturn();
        String walletId = objectMapper.readTree(walletResult.getResponse().getContentAsString()).get("id").asText();

        // Place bet
        var betBody = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "100.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(betBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.odds").value("1.9048"))
                .andExpect(jsonPath("$.potentialPayout").value("190.48"));
    }
}
