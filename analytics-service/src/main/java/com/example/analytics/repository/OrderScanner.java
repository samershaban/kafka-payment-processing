package com.example.analytics.repository;

import com.example.analytics.model.Order;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Reads every order in Cassandra page by page (Cassandra paging state), so memory stays flat no matter how many
 * orders there are. Read-only; used by the backfill.
 */
@Component
public class OrderScanner {

    static final int PAGE_SIZE = 500;

    private final CassandraTemplate cassandra;

    public OrderScanner(CassandraTemplate cassandra) {
        this.cassandra = cassandra;
    }

    /** Calls {@code action} for each order; returns how many there were. */
    public long forEachOrder(Consumer<Order> action) {
        long count = 0;
        Pageable page = CassandraPageRequest.first(PAGE_SIZE);
        while (true) {
            Slice<Order> slice = cassandra.slice(Query.empty().pageRequest(page), Order.class);
            for (Order order : slice) {
                action.accept(order);
                count++;
            }
            if (!slice.hasNext()) {
                return count;
            }
            page = slice.nextPageable();
        }
    }
}
