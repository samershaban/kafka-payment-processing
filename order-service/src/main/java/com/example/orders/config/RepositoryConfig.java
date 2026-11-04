package com.example.orders.config;

import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Two databases: orders in Cassandra, users in Postgres. Their repositories share the repository package,
 * so each Spring Data module only picks up the interfaces that extend its own base type.
 *
 * Kept out of OrderServiceApplication on purpose: test slices like @WebMvcTest skip @Configuration classes,
 * so controller tests don't try to create real repositories.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.example.orders.repository",
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaRepository.class))
@EnableCassandraRepositories(
        basePackages = "com.example.orders.repository",
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CassandraRepository.class))
public class RepositoryConfig {
}
