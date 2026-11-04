package com.example.orders.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A customer account, stored in Postgres (table "users"; "user" is a reserved word in Postgres).
 * The schema is created by Flyway (db/migration/V1__create_users.sql); Hibernate only validates it.
 * Orders reference a user through Order.customerId = User.id.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Always stored trimmed and lowercased (see UserService), so the UNIQUE constraint catches duplicates. */
    @Column(nullable = false, unique = true)
    private String email;

    private String company;

    private String address;

    /** IANA timezone name, e.g. "Europe/London". */
    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private boolean enrolled;

    protected User() {
        // for JPA
    }

    public User(String name, String email, String company, String address, String timezone, boolean enrolled) {
        update(name, email, company, address, timezone, enrolled);
    }

    /** All changes go through here instead of public setters. */
    public void update(String name, String email, String company, String address, String timezone, boolean enrolled) {
        this.name = name;
        this.email = email;
        this.company = company;
        this.address = address;
        this.timezone = timezone;
        this.enrolled = enrolled;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCompany() { return company; }
    public String getAddress() { return address; }
    public String getTimezone() { return timezone; }
    public boolean isEnrolled() { return enrolled; }
}
