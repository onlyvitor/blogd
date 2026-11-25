package com.onlyvitor.blogd.user.service;

import com.onlyvitor.blogd.auth.service.RefreshTokenService;
import com.onlyvitor.blogd.post.service.PostService;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.dto.ChangePasswordRequest;
import com.onlyvitor.blogd.user.dto.UserRequest;
import com.onlyvitor.blogd.user.exception.InvalidPasswordException;
import com.onlyvitor.blogd.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String RAW_PASSWORD = "StrongPass1!";
    private static final String OLD_PASSWORD = "OldPass1!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private PostService postService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        refreshTokenService = mock(RefreshTokenService.class);
        postService = mock(PostService.class);
        userService = new UserService(userRepository, passwordEncoder, refreshTokenService, postService);
    }

    @Test
    void shouldStorePasswordAsBcryptHashWhenCreatingUser() {
        UserRequest request = request();
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedPassword = captor.getValue().getPassword();

        assertThat(storedPassword).isNotEqualTo(RAW_PASSWORD);
        assertThat(storedPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedPassword)).isTrue();
    }

    @Test
    void shouldForceUsuarioRoleWhenNonAdminCreatesUser() {
        UserRequest request = request(Role.ADMIN);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldAllowAdminRoleWhenAdminCreatesUser() {
        UserRequest request = request(Role.ADMIN);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, true);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldStoreUsuarioRoleWhenAdminOmitsRole() {
        UserRequest request = new UserRequest("vitor", RAW_PASSWORD, "vitor@example.com", null);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, true);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldRehashPasswordWhenUpdatingUser() {
        User existing = existingUser(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.update(1L, request(), false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedPassword = captor.getValue().getPassword();

        assertThat(storedPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedPassword)).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedPassword)).isFalse();
    }

    @Test
    void shouldReplaceUsernameAndEmailWhenUpdatingUser() {
        User existing = existingUser(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.update(1L, new UserRequest("novo", RAW_PASSWORD, "novo@example.com", Role.USER), false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("novo");
        assertThat(captor.getValue().getEmail()).isEqualTo("novo@example.com");
    }

    @Test
    void shouldKeepRoleWhenNonAdminUpdatesUser() {
        User existing = existingUser(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.update(1L, request(Role.ADMIN), false);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldUpdateRoleWhenAdminUpdatesUser() {
        User existing = existingUser(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.update(1L, request(Role.ADMIN), true);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldChangePasswordAndRevokeSessionsWhenCurrentPasswordMatches() {
        User existing = existingUser(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword(1L, new ChangePasswordRequest(OLD_PASSWORD, RAW_PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedPassword = captor.getValue().getPassword();

        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedPassword)).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedPassword)).isFalse();
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordDoesNotMatch() {
        User existing = existingUser(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                userService.changePassword(1L, new ChangePasswordRequest("WrongPass1!", RAW_PASSWORD)))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void shouldRevokeSessionsWhenDeletingUser() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.delete(1L);

        verify(refreshTokenService).revokeAllForUser(1L);
        verify(postService).handleAuthorDeletion(1L);
        verify(userRepository).deleteById(1L);
    }

    private User existingUser(Role role) {
        return User.builder()
                .id(1L)
                .username("vitor")
                .password(passwordEncoder.encode(OLD_PASSWORD))
                .email("vitor@example.com")
                .role(role)
                .build();
    }

    private UserRequest request() {
        return request(Role.USER);
    }

    private UserRequest request(Role role) {
        return new UserRequest("vitor", RAW_PASSWORD, "vitor@example.com", role);
    }
}
