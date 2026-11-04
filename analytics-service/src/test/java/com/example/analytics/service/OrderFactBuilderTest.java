package com.example.analytics.service;

import com.example.analytics.events.OrderCreatedEvent;
import com.example.analytics.events.PaymentProcessedEvent;
import com.example.analytics.model.Order;
import com.example.analytics.model.OrderFactEvent.Kind;
import com.example.analytics.repository.OrderReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFactBuilderTest {

    @Mock OrderReadRepository orders;
    @InjectMocks OrderFactBuilder builder;

    private static final Instant CREATED_AT = Instant.parse("2026-11-04T12:00:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-11-04T12:00:01Z");

    private static Order row(String status) {
        return new Order("o1", "u1", "Ramen", 2, new BigDecimal("24.50"), status, null, CREATED_AT, CREATED_AT);
    }

    @Test
    void created_usesTheCassandraRow() {
        when(orders.findById("o1")).thenReturn(Optional.of(row("CREATED")));

        var fact = builder.fromCreated(new OrderCreatedEvent("o1", "u1", "Ramen", 2, new BigDecimal("24.50"), CREATED_AT));

        assertThat(fact.kind()).isEqualTo(Kind.CREATED);
        assertThat(fact.occurredAt()).isEqualTo(CREATED_AT);
        assertThat(fact.order().item()).isEqualTo("Ramen");
        assertThat(fact.order().status()).isEqualTo("CREATED");
    }

    @Test
    void created_describesTheOrderAtCreation_evenIfItIsAlreadyPaidNow() {
        // Processed late (lag, restart, replay): Cassandra already has the final status
        Order paidRow = new Order("o1", "u1", "Ramen", 2, new BigDecimal("24.50"), "PAYMENT_FAILED", "Card declined",
                CREATED_AT, PAID_AT);
        when(orders.findById("o1")).thenReturn(Optional.of(paidRow));

        var fact = builder.fromCreated(new OrderCreatedEvent("o1", "u1", "Ramen", 2, new BigDecimal("24.50"), CREATED_AT));

        assertThat(fact.order().status()).isEqualTo("CREATED");
        assertThat(fact.order().statusReason()).isNull();
        assertThat(fact.order().updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void created_fallsBackToTheEventWhenTheRowIsMissing() {
        when(orders.findById("o1")).thenReturn(Optional.empty());

        var fact = builder.fromCreated(new OrderCreatedEvent("o1", "u1", "Ramen", 2, BigDecimal.TEN, CREATED_AT));

        assertThat(fact.order().customerId()).isEqualTo("u1");
        assertThat(fact.order().status()).isEqualTo("CREATED");
    }

    @Test
    void paymentResult_takesTheOutcomeFromTheEvent_evenIfCassandraIsNotUpdatedYet() {
        // order-service hasn't saved PAYMENT_FAILED yet: the row still says CREATED
        when(orders.findById("o1")).thenReturn(Optional.of(row("CREATED")));

        var fact = builder.fromPaymentResult(new PaymentProcessedEvent("o1", false, "Card declined", PAID_AT)).orElseThrow();

        assertThat(fact.kind()).isEqualTo(Kind.PAYMENT_RESULT);
        assertThat(fact.occurredAt()).isEqualTo(PAID_AT);
        assertThat(fact.order().status()).isEqualTo("PAYMENT_FAILED");
        assertThat(fact.order().statusReason()).isEqualTo("Card declined");
        assertThat(fact.order().updatedAt()).isEqualTo(PAID_AT);
        assertThat(fact.order().totalPrice()).isEqualByComparingTo("24.50");   // details still from Cassandra
    }

    @Test
    void paymentResult_forUnknownOrder_isSkipped() {
        when(orders.findById("missing")).thenReturn(Optional.empty());

        assertThat(builder.fromPaymentResult(new PaymentProcessedEvent("missing", true, null, PAID_AT))).isEmpty();
    }

    @Test
    void eventId_isTheSameForTheSameSourceEvent_soDuplicatesCanBeDropped() {
        when(orders.findById("o1")).thenReturn(Optional.of(row("CREATED")));
        var event = new OrderCreatedEvent("o1", "u1", "Ramen", 2, BigDecimal.TEN, CREATED_AT);

        assertThat(builder.fromCreated(event).eventId()).isEqualTo(builder.fromCreated(event).eventId());
        assertThat(OrderFactBuilder.eventId(Kind.CREATED, "o1"))
                .isNotEqualTo(OrderFactBuilder.eventId(Kind.PAYMENT_RESULT, "o1", PAID_AT.toString()));
    }
}
