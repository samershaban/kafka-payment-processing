package com.example.analytics.model;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only view of order-service's Cassandra table orders.orders. A copy of the mapping (services don't share
 * code); status is kept as text so this service doesn't depend on order-service's enum.
 */
@Table("orders")
public class Order {

    @PrimaryKey
    private String id;

    private String customerId;
    private String item;
    private int quantity;
    private BigDecimal totalPrice;
    private String status;
    private String statusReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected Order() {
        // for Spring Data
    }

    /** For tests. */
    public Order(String id, String customerId, String item, int quantity, BigDecimal totalPrice, String status,
                 String statusReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.item = item;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = status;
        this.statusReason = statusReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getItem() { return item; }
    public int getQuantity() { return quantity; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
