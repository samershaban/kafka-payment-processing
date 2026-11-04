package com.example.orders.service;

import com.example.orders.dto.CreateUserRequest;
import com.example.orders.dto.UpdateUserRequest;
import com.example.orders.exception.EmailInUseException;
import com.example.orders.exception.UserHasOrdersException;
import com.example.orders.exception.UserNotFoundException;
import com.example.orders.model.Order;
import com.example.orders.model.User;
import com.example.orders.repository.OrderRepository;
import com.example.orders.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    @Mock OrderRepository orders;
    @InjectMocks UserService service;

    private static User user() {
        return new User("Ada", "ada@example.com", null, null, "UTC", false);
    }

    @Test
    void create_normalizesEmailAndAppliesDefaults() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User created = service.create(new CreateUserRequest("Ada", "Ada@Example.COM", "  ", null, null, null));

        assertThat(created.getEmail()).isEqualTo("ada@example.com");
        assertThat(created.getTimezone()).isEqualTo("UTC");
        assertThat(created.isEnrolled()).isFalse();
        assertThat(created.getCompany()).isNull();   // blank -> null
    }

    @Test
    void create_rejectsDuplicateEmail_caseInsensitive() {
        when(users.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("Ada", "ADA@example.com", null, null, null, null)))
                .isInstanceOf(EmailInUseException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void create_turnsUniqueConstraintRaceInto409() {
        // Another request inserted the same email between our existence check and our insert
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(new CreateUserRequest("Ada", "ada@example.com", null, null, null, null)))
                .isInstanceOf(EmailInUseException.class);
    }

    @Test
    void get_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void update_changesTheManagedEntity() {
        UUID id = UUID.randomUUID();
        User user = user();
        when(users.findById(id)).thenReturn(Optional.of(user));

        service.update(id, new UpdateUserRequest("Ada L", "ADA@example.com", "Engines", "1 Road", "Europe/London", true));

        assertThat(user.getName()).isEqualTo("Ada L");
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.getTimezone()).isEqualTo("Europe/London");
        assertThat(user.isEnrolled()).isTrue();
        verify(users).flush();
    }

    @Test
    void update_rejectsEmailOfAnotherUser() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user()));
        when(users.existsByEmailAndIdNot("taken@example.com", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id,
                new UpdateUserRequest("Ada", "taken@example.com", null, null, "UTC", false)))
                .isInstanceOf(EmailInUseException.class);
    }

    @Test
    void delete_refusesWhenUserHasOrders() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user()));
        when(orders.findByCustomerId(id.toString()))
                .thenReturn(List.of(new Order(id.toString(), "Ramen", 1, BigDecimal.TEN)));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(UserHasOrdersException.class);
        verify(users, never()).delete(any());
    }

    @Test
    void delete_removesUserWithoutOrders() {
        UUID id = UUID.randomUUID();
        User user = user();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(orders.findByCustomerId(id.toString())).thenReturn(List.of());

        service.delete(id);

        verify(users).delete(user);
    }
}
