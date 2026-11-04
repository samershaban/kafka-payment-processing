package com.example.orders.kafka;

import com.example.orders.domain.OrderRepository;
import com.example.orders.events.PaymentProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderRepository orders;

    public PaymentResultListener(OrderRepository orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = "${app.topics.payment-processed}")
    public void onPaymentProcessed(@Payload PaymentProcessedEvent event,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received PaymentProcessed for {} (success={}) from partition {} @ offset {}",
                event.orderId(), event.success(), partition, offset);

        orders.findById(event.orderId()).ifPresentOrElse(order -> {
            if (event.success()) {
                order.markPaid();
            } else {
                order.markPaymentFailed(event.reason());
            }
            // Cassandra has no change tracking (unlike JPA), so write the update explicitly.
            orders.save(order);
            log.info("Order {} is now {}", order.getId(), order.getStatus());
        }, () -> log.warn("Payment result for unknown order {}", event.orderId()));
    }
}