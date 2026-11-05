package com.example.payments.kafka;

import com.example.payments.events.OrderCreatedEvent;
import com.example.payments.events.PaymentProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String paymentTopic;
    private final double failureRate;
    private final long delayMs;

    public OrderCreatedListener(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${app.topics.payment-processed}") String paymentTopic,
                                @Value("${app.payment.failure-rate}") double failureRate,
                                @Value("${app.payment.processing-delay-ms}") long delayMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentTopic = paymentTopic;
        this.failureRate = failureRate;
        this.delayMs = delayMs;
    }

    @KafkaListener(topics = "${app.topics.order-created}")
    public void onOrderCreated(@Payload OrderCreatedEvent order,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) throws InterruptedException {
        log.info("Charging {} for order {} (partition {}, offset {})",
                order.totalPrice(), order.orderId(), partition, offset);
        int randomDelay = (int)(Math.random()*800)-300; // random delay between -300 and +500 ms
        Thread.sleep(delayMs+randomDelay); // simulate calling a payment provider

        boolean success = ThreadLocalRandom.current().nextDouble() >= failureRate;
        var result = new PaymentProcessedEvent(
                order.orderId(),
                success,
                success ? null : "Card declined",
                Instant.now());

        kafkaTemplate.send(paymentTopic, order.orderId(), result);
        log.info("Payment for order {} {}", order.orderId(), success ? "SUCCEEDED" : "FAILED");
    }
}
