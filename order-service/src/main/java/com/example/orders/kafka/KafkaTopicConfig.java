package com.example.orders.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Spring's KafkaAdmin creates these topics on startup if they don't exist.
 * 3 partitions lets you run up to 3 instances of a consumer in the same group in parallel.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreatedTopic(@Value("${app.topics.order-created}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentProcessedTopic(@Value("${app.topics.payment-processed}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
