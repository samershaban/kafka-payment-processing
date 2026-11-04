package com.example.orders.repository;

import com.example.orders.model.Order;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

/** Orders live in Cassandra (keyspace "orders", table "orders"). */
public interface OrderRepository extends CassandraRepository<Order, String> {

    /** Uses the secondary index on customerid created by {@link CassandraIndexes}. */
    List<Order> findByCustomerId(String customerId);
}
