package com.cryptobet.engine.price;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PriceService priceService;

    @Test
    void getPrice_exists_returnsPrice() throws Exception {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));

        mockMvc.perform(get("/api/prices/{symbol}", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.price").value("65432.10"));
    }

    @Test
    void getPrice_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/prices/{symbol}", "NONEXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllPrices_returnsList() throws Exception {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));

        mockMvc.perform(get("/api/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.symbol=='BTCUSDT')].price", hasItem("65000.00")))
                .andExpect(jsonPath("$[?(@.symbol=='ETHUSDT')].price", hasItem("3500.00")));
    }
}
