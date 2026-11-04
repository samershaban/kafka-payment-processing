package com.example.analytics.service;

import com.example.analytics.cdc.UserCdcCapture;
import com.example.analytics.kafka.AnalyticsPublisher;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Everything /admin/status reports: CDC state, what was published, and how far behind the order listener is. */
@Service
public class StatusService {

    private static final long KAFKA_TIMEOUT_SECONDS = 3;

    private final UserCdcCapture cdc;
    private final AnalyticsPublisher publisher;
    private final AdminClient admin;
    private final String groupId;
    private final Set<String> sourceTopics;

    public StatusService(UserCdcCapture cdc, AnalyticsPublisher publisher, KafkaAdmin kafkaAdmin,
                         @Value("${spring.kafka.consumer.group-id}") String groupId,
                         @Value("${app.topics.orders-created}") String ordersCreated,
                         @Value("${app.topics.payments-processed}") String paymentsProcessed) {
        this.cdc = cdc;
        this.publisher = publisher;
        this.admin = AdminClient.create(kafkaAdmin.getConfigurationProperties());
        this.groupId = groupId;
        this.sourceTopics = Set.of(ordersCreated, paymentsProcessed);
    }

    public record Status(UserCdcCapture.Status cdc, AnalyticsPublisher.Stats published, Lag lag, Instant at) {}

    /** Messages not yet processed by this service's consumer group, per source topic (null if unknown). */
    public record Lag(Map<String, Long> byTopic, long total, String error) {}

    public Status status() {
        return new Status(cdc.status(), publisher.stats(), lag(), Instant.now());
    }

    Lag lag() {
        try {
            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata().get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Set<TopicPartition> partitions = admin.describeTopics(sourceTopics).allTopicNames()
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS).values().stream()
                    .flatMap(t -> t.partitions().stream().map(p -> new TopicPartition(t.name(), p.partition())))
                    .collect(Collectors.toSet());
            var ends = admin.listOffsets(partitions.stream()
                            .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest())))
                    .all().get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, Long> byTopic = new LinkedHashMap<>();
            for (TopicPartition tp : partitions) {
                long end = ends.get(tp).offset();
                OffsetAndMetadata c = committed.get(tp);
                long lag = Math.max(0, end - (c == null ? 0 : c.offset()));
                byTopic.merge(tp.topic(), lag, Long::sum);
            }
            return new Lag(byTopic, byTopic.values().stream().mapToLong(Long::longValue).sum(), null);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Lag(Map.of(), -1, "Could not read consumer lag: " + e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        admin.close();
    }
}
