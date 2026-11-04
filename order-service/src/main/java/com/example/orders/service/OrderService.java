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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    static final int MAX_KEY_LENGTH = 255;

    private final OrderRepository orders;
    private final IdempotencyStore idempotency;
    private final OrderEventPublisher publisher;
    private final UserService users;

    public OrderService(OrderRepository orders, IdempotencyStore idempotency,
                        OrderEventPublisher publisher, UserService users) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.publisher = publisher;
        this.users = users;
    }

    /** The order, and whether this call created it (false = an idempotent replay of an earlier request). */
    public record CreateResult(Order order, boolean created) {}

    /**
     * Creates an order for an existing user. With an idempotency key, repeating the request returns the
     * original order instead of creating a duplicate. Without one, every call creates a new order.
     */
    public CreateResult create(String idempotencyKey, CreateOrderRequest req) {
        requireKnownCustomer(req.customerId());

        if (idempotencyKey == null) {
            return new CreateResult(saveAndPublish(newOrder(req)), true);
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException(MAX_KEY_LENGTH);
        }

        Order order = newOrder(req);
        String hash = hash(req);
        Optional<IdempotencyRecord> existing;
        try {
            existing = idempotency.claim(idempotencyKey, hash, order.getId());
        } catch (IdempotencyStore.KeyNotVisibleException e) {
            throw new IdempotencyInProgressException();
        }

        if (existing.isEmpty()) {
            // We own the key: create the order. If saving fails, free the key so a retry can work.
            try {
                return new CreateResult(saveAndPublish(order), true);
            } catch (RuntimeException e) {
                idempotency.release(idempotencyKey);
                throw e;
            }
        }

        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(hash)) {
            throw new IdempotencyKeyReusedException();
        }
        // Same key, same body: a retry. Return the original order; nothing is saved or published again.
        return orders.findById(record.getOrderId())
                .map(original -> new CreateResult(original, false))
                // The first request claimed the key but hasn't saved the order yet (still in flight)
                .orElseThrow(IdempotencyInProgressException::new);
    }

    public Optional<Order> get(String id) {
        return orders.findById(id);
    }

    // In Cassandra, findAll() scans every partition of the table. Fine for a learning project;
    // in production you'd query by partition key (e.g. a separate orders_by_customer table).
    public List<Order> list() {
        return orders.findAll();
    }

    /** Newest first. The caller checks the user exists. */
    public List<Order> forCustomer(UUID userId) {
        return orders.findByCustomerId(userId.toString()).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .toList();
    }

    /** Called by the Kafka listener when the Payment Service reports a result. */
    public Optional<Order> applyPaymentResult(String orderId, boolean success, String reason) {
        return orders.findById(orderId).map(order -> {
            if (success) {
                order.markPaid();
            } else {
                order.markPaymentFailed(reason);
            }
            // Cassandra has no change tracking (unlike JPA), so write the update explicitly.
            return orders.save(order);
        });
    }

    /** customerId must be the UUID of an existing user (Postgres). */
    private void requireKnownCustomer(String customerId) {
        UUID id;
        try {
            id = UUID.fromString(customerId);
        } catch (IllegalArgumentException e) {
            throw new UnknownCustomerException(customerId);
        }
        if (!users.exists(id)) {
            throw new UnknownCustomerException(customerId);
        }
    }

    private Order saveAndPublish(Order order) {
        Order saved = orders.save(order);

        // NOTE: saving and publishing are two separate operations. If the app crashes between them,
        // the DB and Kafka disagree. That's the problem the "transactional outbox" pattern solves
        // (stage 5 of the project).
        publisher.publishOrderCreated(saved);
        return saved;
    }

    private static Order newOrder(CreateOrderRequest req) {
        return new Order(req.customerId(), req.item(), req.quantity(), req.totalPrice());
    }

    /** Fingerprint of the request. stripTrailingZeros() makes 24.5 and 24.50 count as the same price. */
    static String hash(CreateOrderRequest req) {
        String canonical = String.join("\u0000",
                req.customerId(), req.item(), Integer.toString(req.quantity()),
                req.totalPrice().stripTrailingZeros().toPlainString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
