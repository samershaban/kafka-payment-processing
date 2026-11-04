package com.example.analytics.service;

import com.example.analytics.kafka.AnalyticsPublisher;
import com.example.analytics.model.Order;
import com.example.analytics.model.OrderFactEvent;
import com.example.analytics.model.OrderFactEvent.Kind;
import com.example.analytics.repository.OrderReadRepository;
import com.example.analytics.repository.OrderScanner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackfillServiceTest {

    private static Order order(String id, String status) {
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        return new Order(id, "u1", "Ramen", 1, BigDecimal.TEN, status, null, t, t.plusSeconds(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesOneBackfillFactPerOrder_withTheCurrentStatus() {
        OrderScanner scanner = mock(OrderScanner.class);
        AnalyticsPublisher publisher = mock(AnalyticsPublisher.class);
        when(scanner.forEachOrder(any())).thenAnswer(inv -> {
            Consumer<Order> action = inv.getArgument(0);
            List.of(order("o1", "PAID"), order("o2", "PAYMENT_FAILED")).forEach(action);
            return 2L;
        });
        var service = new BackfillService(scanner, new OrderFactBuilder(mock(OrderReadRepository.class)), publisher);

        var result = service.backfillOrders();

        assertThat(result.published()).isEqualTo(2);
        ArgumentCaptor<OrderFactEvent> facts = ArgumentCaptor.forClass(OrderFactEvent.class);
        verify(publisher, times(2)).publish(facts.capture());
        assertThat(facts.getAllValues()).allSatisfy(f -> assertThat(f.kind()).isEqualTo(Kind.BACKFILL));
        // A backfill fact is the order as it is now (unlike a CREATED fact)
        assertThat(facts.getAllValues()).extracting(f -> f.order().status()).containsExactly("PAID", "PAYMENT_FAILED");
    }

    @Test
    void repeatedBackfill_producesTheSameEventIds() {
        OrderFactBuilder builder = new OrderFactBuilder(mock(OrderReadRepository.class));
        Order o = order("o1", "PAID");
        assertThat(builder.fromBackfill(o).eventId()).isEqualTo(builder.fromBackfill(o).eventId());
    }
}
