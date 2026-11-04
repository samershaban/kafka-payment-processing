package com.example.orders.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled methods (e.g. the SSE heartbeat in OrderUpdates). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
