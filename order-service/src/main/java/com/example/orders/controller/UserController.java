package com.example.orders.controller;

import com.example.orders.dto.CreateUserRequest;
import com.example.orders.dto.UpdateUserRequest;
import com.example.orders.dto.UserResponse;
import com.example.orders.model.Order;
import com.example.orders.model.User;
import com.example.orders.service.OrderService;
import com.example.orders.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** HTTP only: validation, status codes and DTO mapping. The logic lives in UserService. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService users;
    private final OrderService orders;

    public UserController(UserService users, OrderService orders) {
        this.users = users;
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        User user = users.create(req);
        return ResponseEntity.created(URI.create("/users/" + user.getId())).body(UserResponse.from(user));
    }

    /** All users, or only enrolled / not enrolled ones with ?enrolled=true|false. */
    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) Boolean enrolled) {
        return users.list(enrolled).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(users.get(id));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return UserResponse.from(users.update(id, req));
    }

    /** 409 if the user has orders. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        users.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** The user's orders, newest first. 404 if the user doesn't exist. */
    @GetMapping("/{id}/orders")
    public List<Order> orders(@PathVariable UUID id) {
        users.get(id);
        return orders.forCustomer(id);
    }
}
