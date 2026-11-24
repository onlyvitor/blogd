package com.onlyvitor.blogd.user.domain;

import com.onlyvitor.blogd.user.dto.UserRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Role role;

    public static User from(UserRequest request, String encodedPassword, boolean isAdmin) {
        return User.builder()
                .username(request.username())
                .password(encodedPassword)
                .email(request.email())
                .role(isAdmin ? request.role() : Role.USUARIO)
                .build();
    }

    public void update(UserRequest request, String encodedPassword, boolean isAdmin) {
        this.username = request.username();
        this.password = encodedPassword;
        this.email = request.email();
        if (isAdmin) {
            this.role = request.role();
        }
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
