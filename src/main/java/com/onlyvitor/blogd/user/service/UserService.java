package com.onlyvitor.blogd.user.service;

import com.onlyvitor.blogd.auth.service.RefreshTokenService;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.dto.ChangePasswordRequest;
import com.onlyvitor.blogd.user.dto.UserRequest;
import com.onlyvitor.blogd.user.dto.UserResponse;
import com.onlyvitor.blogd.user.exception.InvalidPasswordException;
import com.onlyvitor.blogd.user.exception.UserAlreadyExistsException;
import com.onlyvitor.blogd.user.exception.UserNotFoundException;
import com.onlyvitor.blogd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse create(UserRequest request, boolean isAdmin) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(isAdmin ? request.getRole() : Role.USUARIO)
                .build();

        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request, boolean isAdmin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (!user.getUsername().equals(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(request.getUsername());
        }

        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        if (isAdmin) {
            user.setRole(request.getRole());
        }

        User updated = userRepository.save(user);
        return UserResponse.from(updated);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException();
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(id);
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        refreshTokenService.revokeAllForUser(id);
        userRepository.deleteById(id);
    }
}
