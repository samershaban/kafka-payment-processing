package com.example.analytics.repository;

import com.example.analytics.model.Order;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Read-only access to order-service's Cassandra orders table. Extends the bare Repository marker (not
 * CassandraRepository) so no save/delete methods exist: this service must never write order data.
 */
public interface OrderReadRepository extends Repository<Order, String> {

    /** Primary-key lookup: a single-partition read, cheap even at high volume. */
    Optional<Order> findById(String id);
}
