package com.example.wallet;

import com.example.wallet.wallet.application.LedgerReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "wallet.funding-scheduler.enabled=false")
@AutoConfigureMockMvc
class WalletIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired LedgerReconciliationService reconciliation;

    @Test
    void createsWalletWithOpeningBalance() throws Exception {
        mvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 123.45}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.balance").value(123.45));

        assertThat(reconciliation.verifyAll().consistent()).isTrue();
    }

    @Test
    void rejectsNegativeOpeningBalance() throws Exception {
        mvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": -0.01}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
