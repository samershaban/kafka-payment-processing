package com.example.orders.repository;

import com.example.orders.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Users live in Postgres (table "users", schema managed by Flyway). */
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByEnrolled(boolean enrolled);

    boolean existsByEmail(String email);

    /** For updates: is the email taken by a different user? */
    boolean existsByEmailAndIdNot(String email, UUID id);
}
