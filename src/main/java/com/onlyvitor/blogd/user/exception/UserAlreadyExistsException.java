package com.onlyvitor.blogd.user.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String username) {
        super("A user already exists with username: " + username);
    }
}
