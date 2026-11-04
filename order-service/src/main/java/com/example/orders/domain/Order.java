package com.example.orders.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stored in the Cassandra table orders.orders. Column types: BigDecimal -> decimal,
 * Instant -> timestamp, OrderStatus -> text (the enum name).
 */
@Table("orders")
public class Order {

    @PrimaryKey
    private String id;

    private String customerId;
    private String item;
    private int quantity;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private String statusReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected Order() {
        // for Spring Data
    }

    public Order(String customerId, String item, int quantity, BigDecimal totalPrice) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.item = item;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markPaid() {
        this.status = OrderStatus.PAID;
        this.statusReason = null;
        this.updatedAt = Instant.now();
    }

    public void markPaymentFailed(String reason) {
        this.status = OrderStatus.PAYMENT_FAILED;
        this.statusReason = reason;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getItem() { return item; }
    public int getQuantity() { return quantity; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public OrderStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
