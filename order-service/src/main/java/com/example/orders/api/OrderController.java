package com.example.orders.api;

import com.example.orders.domain.IdempotencyRecord;
import com.example.orders.domain.IdempotencyStore;
import com.example.orders.domain.Order;
import com.example.orders.domain.OrderRepository;
import com.example.orders.kafka.OrderEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final int MAX_KEY_LENGTH = 255;

    private final OrderRepository orders;
    private final OrderEventPublisher publisher;
    private final IdempotencyStore idempotency;

    public OrderController(OrderRepository orders, OrderEventPublisher publisher, IdempotencyStore idempotency) {
        this.orders = orders;
        this.publisher = publisher;
        this.idempotency = idempotency;
    }

    /**
     * Creates an order. Send an {@code Idempotency-Key} header (e.g. a UUID, one per order you mean to place)
     * to make retries safe: repeating the request with the same key returns the original order (200)
     * instead of creating a duplicate. Without the header, every request creates a new order.
     */
    @PostMapping
    public ResponseEntity<Order> create(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                        @Valid @RequestBody CreateOrderRequest req) {
        if (key == null) {
            return createNew(req);
        }
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 1-" + MAX_KEY_LENGTH + " characters");
        }

        Order order = newOrder(req);
        String hash = hash(req);
        Optional<IdempotencyRecord> existing = idempotency.claim(key, hash, order.getId());

        if (existing.isEmpty()) {
            // We own the key: create the order. If saving fails, free the key so a retry can work.
            try {
                return created(save(order));
            } catch (RuntimeException e) {
                idempotency.release(key);
                throw e;
            }
        }

        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used for a different order");
        }
        // Same key, same body: a retry. Return the original order; nothing is saved or published again.
        return orders.findById(record.getOrderId())
                .map(ResponseEntity::ok)
                // The first request claimed the key but hasn't saved the order yet (still in flight)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "An order with this Idempotency-Key is still being created; retry shortly"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable String id) {
        return orders.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // In Cassandra, findAll() scans every partition of the table. Fine for a learning project;
    // in production you'd query by partition key (e.g. a separate orders_by_customer table).
    @GetMapping
    public List<Order> list() {
        return orders.findAll();
    }

    private ResponseEntity<Order> createNew(CreateOrderRequest req) {
        return created(save(newOrder(req)));
    }

    private Order save(Order order) {
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

    private static ResponseEntity<Order> created(Order order) {
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(order);
    }

    /** Fingerprint of the request. stripTrailingZeros() makes 24.5 and 24.50 count as the same price. */
    private static String hash(CreateOrderRequest req) {
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
