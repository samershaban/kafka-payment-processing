package com.example.orders.service;

import com.example.orders.dto.CreateUserRequest;
import com.example.orders.dto.UpdateUserRequest;
import com.example.orders.exception.EmailInUseException;
import com.example.orders.exception.UserHasOrdersException;
import com.example.orders.exception.UserNotFoundException;
import com.example.orders.model.User;
import com.example.orders.repository.OrderRepository;
import com.example.orders.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    static final String DEFAULT_TIMEZONE = "UTC";

    private final UserRepository users;
    private final OrderRepository orders;

    public UserService(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    public User create(CreateUserRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw new EmailInUseException(email);
        }
        User user = new User(
                req.name().trim(),
                email,
                blankToNull(req.company()),
                blankToNull(req.address()),
                req.timezone() == null ? DEFAULT_TIMEZONE : req.timezone(),
                Boolean.TRUE.equals(req.enrolled()));
        try {
            // Flush now so a UNIQUE(email) violation from a concurrent request surfaces here, as a 409
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailInUseException(email);
        }
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<User> list(Boolean enrolled) {
        return enrolled == null ? users.findAll() : users.findByEnrolled(enrolled);
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return users.existsById(id);
    }

    public User update(UUID id, UpdateUserRequest req) {
        User user = get(id);
        String email = normalizeEmail(req.email());
        if (users.existsByEmailAndIdNot(email, id)) {
            throw new EmailInUseException(email);
        }
        // No save() needed: the entity is managed, so JPA writes the changes when the transaction commits
        user.update(req.name().trim(), email, blankToNull(req.company()), blankToNull(req.address()),
                req.timezone(), req.enrolled());
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EmailInUseException(email);
        }
        return user;
    }

    /** Users who have placed orders can't be deleted, so no order is left pointing at a missing customer. */
    public void delete(UUID id) {
        User user = get(id);
        int orderCount = orders.findByCustomerId(id.toString()).size();
        if (orderCount > 0) {
            throw new UserHasOrdersException(id, orderCount);
        }
        users.delete(user);
    }

    /** "Ada@Example.com " and "ada@example.com" are the same address. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
