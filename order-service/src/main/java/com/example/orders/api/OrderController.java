package com.example.orders.api;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderRepository;
import com.example.orders.kafka.OrderEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orders;
    private final OrderEventPublisher publisher;

    public OrderController(OrderRepository orders, OrderEventPublisher publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest req) {
        Order order = orders.save(new Order(req.customerId(), req.item(), req.quantity(), req.totalPrice()));

        // NOTE: saving and publishing are two separate operations. If the app crashes between them,
        // the DB and Kafka disagree. That's the problem the "transactional outbox" pattern solves
        // (stage 5 of the project).
        publisher.publishOrderCreated(order);

        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable String id) {
        return orders.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Order> list() {
        return orders.findAll();
    }
}
