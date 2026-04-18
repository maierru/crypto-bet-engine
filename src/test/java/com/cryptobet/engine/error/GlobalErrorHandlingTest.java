package com.cryptobet.engine.error;

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
class GlobalErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Validation errors return 400 with consistent format ---

    @Test
    void placeBet_missingRequiredFields_returns400WithValidationError() throws Exception {
        var body = "{}";

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.walletId").exists())
                .andExpect(jsonPath("$.details.symbol").exists())
                .andExpect(jsonPath("$.details.direction").exists())
                .andExpect(jsonPath("$.details.stake").exists())
                .andExpect(jsonPath("$.details.entryPrice").exists());
    }

    @Test
    void placeBet_negativeStake_returns400WithValidationError() throws Exception {
        String walletId = createWalletAndGetId("100.00");
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.stake").exists());
    }

    @Test
    void createWallet_negativeBalance_returns400WithValidationError() throws Exception {
        var body = """
                {"initialBalance": "-50.00"}
                """;

        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.initialBalance").exists());
    }

    @Test
    void deposit_nullAmount_returns400WithValidationError() throws Exception {
        String walletId = createWalletAndGetId("100.00");

        mockMvc.perform(post("/api/wallets/{id}/deposit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.amount").exists());
    }

    // --- Not found returns 404 with consistent format ---

    @Test
    void getWallet_notFound_returns404WithConsistentFormat() throws Exception {
        mockMvc.perform(get("/api/wallets/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getBet_notFound_returns404WithConsistentFormat() throws Exception {
        mockMvc.perform(get("/api/bets/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    // --- Insufficient balance returns 409 ---

    @Test
    void placeBet_insufficientBalance_returns409() throws Exception {
        String walletId = createWalletAndGetId("10.00");

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").exists());
    }

    // --- Unhandled exception returns 500 with generic message ---

    @Test
    void deposit_nonExistentWallet_returns404NotStackTrace() throws Exception {
        mockMvc.perform(post("/api/wallets/{id}/deposit", "00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "50.00"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message", not(containsString("Exception"))))
                .andExpect(jsonPath("$.message", not(containsString("at com."))));
    }

    private String createWalletAndGetId(String balance) throws Exception {
        var body = """
                {"initialBalance": "%s"}
                """.formatted(balance);

        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
