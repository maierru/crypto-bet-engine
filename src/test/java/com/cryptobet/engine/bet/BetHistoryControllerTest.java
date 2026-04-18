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
class BetHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BetRepository betRepository;

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

    private String placeBetAndGetId(String walletId, String symbol, String direction) throws Exception {
        var body = """
                {
                    "walletId": "%s",
                    "symbol": "%s",
                    "direction": "%s",
                    "stake": "10.00",
                    "entryPrice": "65000.00"
                }
                """.formatted(walletId, symbol, direction);

        MvcResult result = mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void listBetsForWallet_returnsPaginatedResultsSortedByCreatedAtDesc() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("1000.00"));

        placeBetAndGetId(walletId, "BTCUSDT", "UP");
        placeBetAndGetId(walletId, "ETHUSDT", "DOWN");
        placeBetAndGetId(walletId, "SOLUSDT", "UP");

        mockMvc.perform(get("/api/wallets/{walletId}/bets", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].symbol").value("SOLUSDT"))
                .andExpect(jsonPath("$.content[1].symbol").value("ETHUSDT"))
                .andExpect(jsonPath("$.content[2].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void listBetsForWallet_filterByStatus_returnsOnlyMatching() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("1000.00"));

        String betId1 = placeBetAndGetId(walletId, "BTCUSDT", "UP");
        placeBetAndGetId(walletId, "ETHUSDT", "DOWN");

        // Manually settle one bet to WON
        var bet = betRepository.findById(java.util.UUID.fromString(betId1)).orElseThrow();
        bet.setStatus(BetStatus.WON);
        betRepository.save(bet);

        mockMvc.perform(get("/api/wallets/{walletId}/bets", walletId)
                        .param("status", "WON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(betId1))
                .andExpect(jsonPath("$.content[0].status").value("WON"));
    }

    @Test
    void listBetsForWallet_singleBetIncludesAllFields() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("1000.00"));

        placeBetAndGetId(walletId, "BTCUSDT", "UP");

        mockMvc.perform(get("/api/wallets/{walletId}/bets", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].walletId").value(walletId))
                .andExpect(jsonPath("$.content[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.content[0].direction").value("UP"))
                .andExpect(jsonPath("$.content[0].stake").value("10.00"))
                .andExpect(jsonPath("$.content[0].entryPrice").value("65000.00"))
                .andExpect(jsonPath("$.content[0].odds").exists())
                .andExpect(jsonPath("$.content[0].potentialPayout").exists())
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }

    @Test
    void listBetsForWallet_emptyWallet_returnsEmptyList() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("100.00"));

        mockMvc.perform(get("/api/wallets/{walletId}/bets", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listBetsForWallet_paginationWorks() throws Exception {
        String walletId = createWalletAndGetId(new BigDecimal("1000.00"));

        for (int i = 0; i < 5; i++) {
            placeBetAndGetId(walletId, "BTCUSDT", "UP");
        }

        // Page 0, size 2
        mockMvc.perform(get("/api/wallets/{walletId}/bets", walletId)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0));

        // Page 2 (last page with 1 element)
        mockMvc.perform(get("/api/wallets/{walletId}/bets", walletId)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.number").value(2));
    }
}
