package com.example.analytics.controller;

import com.example.analytics.cdc.UserCdcCapture;
import com.example.analytics.kafka.AnalyticsPublisher;
import com.example.analytics.service.BackfillService;
import com.example.analytics.service.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean BackfillService backfill;
    @MockitoBean StatusService status;

    @Test
    void status_reportsCdcPublishedAndLag() throws Exception {
        when(status.status()).thenReturn(new StatusService.Status(
                new UserCdcCapture.Status(UserCdcCapture.State.RUNNING, 7, Instant.parse("2026-11-04T12:00:00Z"), null),
                new AnalyticsPublisher.Stats(79, null, 7, null),
                new StatusService.Lag(Map.of("orders.created", 0L, "payments.processed", 2L), 2, null),
                Instant.parse("2026-11-04T12:00:05Z")));

        mvc.perform(get("/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cdc.state").value("RUNNING"))
                .andExpect(jsonPath("$.published.orders").value(79))
                .andExpect(jsonPath("$.lag.total").value(2))
                .andExpect(jsonPath("$.lag.byTopic['payments.processed']").value(2));
    }

    @Test
    void backfill_returnsHowManyOrdersWerePublished() throws Exception {
        when(backfill.backfillOrders()).thenReturn(new BackfillService.Result(65, Duration.ofMillis(120)));

        mvc.perform(post("/admin/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(65))
                .andExpect(jsonPath("$.tookMs").value(120));
    }

    @Test
    void backfill_whileOneIsRunning_is409() throws Exception {
        when(backfill.backfillOrders()).thenThrow(new BackfillService.BackfillAlreadyRunningException());

        mvc.perform(post("/admin/backfill")).andExpect(status().isConflict());
    }
}
