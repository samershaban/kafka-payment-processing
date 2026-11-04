package com.example.orders.domain;

import org.springframework.data.cassandra.repository.CassandraRepository;

public interface OrderRepository extends CassandraRepository<Order, String> {
}
