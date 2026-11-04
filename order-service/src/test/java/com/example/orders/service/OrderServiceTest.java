package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.exception.IdempotencyInProgressException;
import com.example.orders.exception.IdempotencyKeyReusedException;
import com.example.orders.exception.InvalidIdempotencyKeyException;
import com.example.orders.exception.UnknownCustomerException;
import com.example.orders.kafka.OrderEventPublisher;
import com.example.orders.model.IdempotencyRecord;
import com.example.orders.model.Order;
import com.example.orders.repository.IdempotencyStore;
import com.example.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock IdempotencyStore idempotency;
    @Mock OrderEventPublisher publisher;
    @Mock UserService users;
    @Mock OrderUpdates updates;
    @InjectMocks OrderService service;

    private final UUID customer = UUID.randomUUID();
    private CreateOrderRequest req;

    @BeforeEach
    void setUp() {
        req = new CreateOrderRequest(customer.toString(), "Ramen", 2, new BigDecimal("24.50"));
        when(users.exists(customer)).thenReturn(true);
        when(orders.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void rejectsCustomerIdThatIsNotAUuid() {
        var bad = new CreateOrderRequest("c-1", "Ramen", 1, BigDecimal.TEN);

        assertThatThrownBy(() -> service.create(null, bad)).isInstanceOf(UnknownCustomerException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void rejectsCustomerThatDoesNotExist() {
        when(users.exists(customer)).thenReturn(false);

        assertThatThrownBy(() -> service.create(null, req)).isInstanceOf(UnknownCustomerException.class);
        verify(orders, never()).save(any());
        verify(publisher, never()).publishOrderCreated(any());
    }

    @Test
    void withoutKey_alwaysCreates() {
        var result = service.create(null, req);

        assertThat(result.created()).isTrue();
        verify(orders).save(result.order());
        verify(publisher).publishOrderCreated(result.order());
        verify(updates).publish(result.order());   // pushed to browsers (SSE)
    }

    @Test
    void paymentResult_updatesOrderAndPushesIt() {
        Order order = new Order(customer.toString(), "Ramen", 2, new BigDecimal("24.50"));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        var updated = service.applyPaymentResult(order.getId(), false, "Card declined");

        assertThat(updated).isPresent();
        assertThat(order.getStatus()).hasToString("PAYMENT_FAILED");
        assertThat(order.getStatusReason()).isEqualTo("Card declined");
        verify(updates).publish(order);
    }

    @Test
    void paymentResultForUnknownOrder_pushesNothing() {
        when(orders.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.applyPaymentResult("missing", true, null)).isEmpty();
        verify(updates, never()).publish(any());
    }

    @Test
    void newKey_createsAndPublishes() {
        when(idempotency.claim(eq("k1"), anyString(), anyString())).thenReturn(Optional.empty());

        var result = service.create("k1", req);

        assertThat(result.created()).isTrue();
        verify(publisher).publishOrderCreated(result.order());
    }

    @Test
    void sameKeySameBody_returnsOriginalWithoutSavingOrPublishing() {
        Order original = new Order(customer.toString(), "Ramen", 2, new BigDecimal("24.50"));
        when(idempotency.claim(eq("k1"), anyString(), anyString()))
                .thenReturn(Optional.of(new IdempotencyRecord("k1", OrderService.hash(req), original.getId())));
        when(orders.findById(original.getId())).thenReturn(Optional.of(original));

        var result = service.create("k1", req);

        assertThat(result.created()).isFalse();
        assertThat(result.order()).isSameAs(original);
        verify(orders, never()).save(any());
        verify(publisher, never()).publishOrderCreated(any());
        verify(updates, never()).publish(any());   // a replay is not a change
    }

    @Test
    void priceFormattingDoesNotChangeTheHash() {
        var sameOrder = new CreateOrderRequest(customer.toString(), "Ramen", 2, new BigDecimal("24.5"));
        assertThat(OrderService.hash(sameOrder)).isEqualTo(OrderService.hash(req));
    }

    @Test
    void sameKeyDifferentBody_isRejected() {
        when(idempotency.claim(eq("k1"), anyString(), anyString()))
                .thenReturn(Optional.of(new IdempotencyRecord("k1", "other-hash", "o1")));

        assertThatThrownBy(() -> service.create("k1", req)).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void sameKeyWhileFirstRequestStillSaving_asksClientToRetry() {
        when(idempotency.claim(eq("k1"), anyString(), anyString()))
                .thenReturn(Optional.of(new IdempotencyRecord("k1", OrderService.hash(req), "not-saved-yet")));
        when(orders.findById("not-saved-yet")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("k1", req)).isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void lostClaimButRecordNotVisible_isNeverTreatedAsAWin() {
        when(idempotency.claim(eq("k1"), anyString(), anyString()))
                .thenThrow(new IdempotencyStore.KeyNotVisibleException("k1"));

        assertThatThrownBy(() -> service.create("k1", req)).isInstanceOf(IdempotencyInProgressException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void saveFailure_releasesTheKeySoARetryCanSucceed() {
        when(idempotency.claim(eq("k1"), anyString(), anyString())).thenReturn(Optional.empty());
        when(orders.save(any(Order.class))).thenThrow(new RuntimeException("Cassandra down"));

        assertThatThrownBy(() -> service.create("k1", req)).hasMessage("Cassandra down");
        verify(idempotency).release("k1");
    }

    @Test
    void blankOrTooLongKey_isRejected() {
        assertThatThrownBy(() -> service.create(" ", req)).isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> service.create("x".repeat(256), req)).isInstanceOf(InvalidIdempotencyKeyException.class);
    }
}
