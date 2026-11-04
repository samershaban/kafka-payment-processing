package com.example.analytics.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The raw analytics topics this service publishes to (one per kind of data). Spring's KafkaAdmin creates them on
 * startup if they don't exist. 12 partitions so up to 12 consumers (e.g. Flink tasks) can read in parallel.
 * Records are keyed by entity id (user id / order id), so each entity's changes stay in order.
 */
@Configuration
public class KafkaTopicsConfig {

    static final int PARTITIONS = 12;

    /** Order and payment facts: one record per order change, key = order id. */
    @Bean
    public NewTopic ordersRawTopic(@Value("${app.topics.orders-raw}") String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }

    /** User changes from Postgres (change data capture), key = user id. */
    @Bean
    public NewTopic usersRawTopic(@Value("${app.topics.users-raw}") String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
