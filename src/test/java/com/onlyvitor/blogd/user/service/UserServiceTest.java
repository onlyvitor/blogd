package com.onlyvitor.blogd.user.service;

import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.dto.UserRequest;
import com.onlyvitor.blogd.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String RAW_PASSWORD = "StrongPass1!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void shouldStorePasswordAsBcryptHashWhenCreatingUser() {
        UserRequest request = request();
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        userService.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedPassword = captor.getValue().getPassword();

        assertThat(storedPassword).isNotEqualTo(RAW_PASSWORD);
        assertThat(storedPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedPassword)).isTrue();
    }

    @Test
    void shouldRehashPasswordWhenUpdatingUser() {
        User existing = User.builder()
                .id(1L)
                .username("vitor")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("vitor@example.com")
                .role(Role.USUARIO)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.update(1L, request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedPassword = captor.getValue().getPassword();

        assertThat(storedPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedPassword)).isTrue();
        assertThat(passwordEncoder.matches("OldPass1!", storedPassword)).isFalse();
    }

    private UserRequest request() {
        return UserRequest.builder()
                .username("vitor")
                .password(RAW_PASSWORD)
                .email("vitor@example.com")
                .role(Role.USUARIO)
                .build();
    }
}
