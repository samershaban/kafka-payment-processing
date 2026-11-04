package com.example.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Analytics capture service: streams user changes from Postgres and order/payment facts (from Kafka events plus
 * Cassandra reads) into the analytics.* Kafka topics, where Flink refines them. See analytics-plan.md.
 */
@SpringBootApplication
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
