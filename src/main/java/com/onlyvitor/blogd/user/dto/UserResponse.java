package com.onlyvitor.blogd.user.dto;

import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;


// static factory
public record UserResponse(
         Long id,
         String username,
         String email,
         Role role
){
    public static UserResponse from(User user){

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    public static UserResponse from(UserAuthenticated user){
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}