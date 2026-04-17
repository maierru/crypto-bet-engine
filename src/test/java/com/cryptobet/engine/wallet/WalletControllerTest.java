package com.cryptobet.engine.wallet;

import com.cryptobet.engine.wallet.dto.CreateWalletRequest;
import com.cryptobet.engine.wallet.dto.DepositRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createWallet_returns201WithUuidAndInitialBalance() throws Exception {
        var request = new CreateWalletRequest(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.balance").value("100.00"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getWallet_returnsCorrectBalance() throws Exception {
        // Create wallet
        var request = new CreateWalletRequest(new BigDecimal("250.50"));
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Get wallet
        mockMvc.perform(get("/api/wallets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.balance").value("250.50"));
    }

    @Test
    void deposit_increasesBalance() throws Exception {
        // Create wallet with 100
        var createReq = new CreateWalletRequest(new BigDecimal("100.00"));
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Deposit 50
        var depositReq = new DepositRequest(new BigDecimal("50.00"));
        mockMvc.perform(post("/api/wallets/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("150.00"));
    }

    @Test
    void deposit_withZeroAmount_returns400() throws Exception {
        var createReq = new CreateWalletRequest(new BigDecimal("100.00"));
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        var depositReq = new DepositRequest(BigDecimal.ZERO);
        mockMvc.perform(post("/api/wallets/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_withNegativeAmount_returns400() throws Exception {
        var createReq = new CreateWalletRequest(new BigDecimal("100.00"));
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        var depositReq = new DepositRequest(new BigDecimal("-10.00"));
        mockMvc.perform(post("/api/wallets/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWallet_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/wallets/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentDeposits_doNotLoseMoney() throws Exception {
        // Create wallet with 0 balance
        var createReq = new CreateWalletRequest(BigDecimal.ZERO);
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // 10 concurrent deposits of $10 each
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    var depositReq = new DepositRequest(new BigDecimal("10.00"));
                    mockMvc.perform(post("/api/wallets/{id}/deposit", id)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(depositReq)))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Final balance should be exactly $100
        mockMvc.perform(get("/api/wallets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("100.00"));
    }
}
