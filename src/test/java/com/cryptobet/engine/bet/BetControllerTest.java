package com.cryptobet.engine.bet;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class BetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void placeBet_returns201WithBetDetails() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("500.00"));

        var body = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "50.00",
                    "entryPrice": "65000.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.direction").value("UP"))
                .andExpect(jsonPath("$.stake").value("50.00"))
                .andExpect(jsonPath("$.entryPrice").value("65000.00"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void placeBet_deductsStakeFromWallet() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("200.00"));

        var body = """
                {
                    "walletId": "%s",
                    "symbol": "ETHUSDT",
                    "direction": "DOWN",
                    "stake": "75.00",
                    "entryPrice": "3500.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Verify wallet balance decreased
        mockMvc.perform(get("/api/wallets/{id}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("125.00"));
    }

    @Test
    void placeBet_insufficientBalance_returns400() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("10.00"));

        var body = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "100.00",
                    "entryPrice": "65000.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeBet_invalidWallet_returns404() throws Exception {
        var body = """
                {
                    "walletId": "00000000-0000-0000-0000-000000000000",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "50.00",
                    "entryPrice": "65000.00"
                }
                """;

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void placeBet_zeroStake_returns400() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("100.00"));

        var body = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "0.00",
                    "entryPrice": "65000.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeBet_negativeStake_returns400() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("100.00"));

        var body = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "-10.00",
                    "entryPrice": "65000.00"
                }
                """.formatted(walletId);

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBet_returnsCorrectBet() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("500.00"));

        var body = """
                {
                    "walletId": "%s",
                    "symbol": "BTCUSDT",
                    "direction": "UP",
                    "stake": "50.00",
                    "entryPrice": "65000.00"
                }
                """.formatted(walletId);

        MvcResult result = mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String betId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/bets/{id}", betId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(betId))
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.direction").value("UP"))
                .andExpect(jsonPath("$.stake").value("50.00"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getBet_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/bets/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
