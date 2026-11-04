package com.example.orders.kafka;

import com.example.orders.events.PaymentProcessedEvent;
import com.example.orders.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Entry point for payment results from Kafka. Like a controller, it delegates to the service layer. */
@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderService orders;

    public PaymentResultListener(OrderService orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = "${app.topics.payment-processed}")
    public void onPaymentProcessed(@Payload PaymentProcessedEvent event,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received PaymentProcessed for {} (success={}) from partition {} @ offset {}",
                event.orderId(), event.success(), partition, offset);

        orders.applyPaymentResult(event.orderId(), event.success(), event.reason()).ifPresentOrElse(
                order -> log.info("Order {} is now {}", order.getId(), order.getStatus()),
                () -> log.warn("Payment result for unknown order {}", event.orderId()));
    }
}
