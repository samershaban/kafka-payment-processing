package com.example.orders.controller;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.model.Order;
import com.example.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.List;

/** HTTP only: validation, status codes and headers. The logic lives in OrderService. */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * Creates an order. customerId must be an existing user's id (otherwise 422).
     * Send an {@code Idempotency-Key} header (e.g. a UUID, one per order you mean to place) to make retries
     * safe: repeating the request with the same key returns the original order (200) instead of creating a
     * duplicate. Without the header, every request creates a new order.
     */
    @PostMapping
    public ResponseEntity<Order> create(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                        @Valid @RequestBody CreateOrderRequest req) {
        OrderService.CreateResult result = orders.create(key, req);
        Order order = result.order();
        if (!result.created()) {
            return ResponseEntity.ok(order);
        }
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(order);
    }

    /**
     * Live updates (Server-Sent Events): an "order" event with the order as JSON whenever an order is created or
     * its status changes. Try it: curl -N http://localhost:8081/orders/stream
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return orders.subscribe();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable String id) {
        return orders.get(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Order> list() {
        return orders.list();
    }
}
