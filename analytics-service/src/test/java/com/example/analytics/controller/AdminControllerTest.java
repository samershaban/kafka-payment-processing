package com.example.analytics.controller;

import com.example.analytics.service.BackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean BackfillService backfill;

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
