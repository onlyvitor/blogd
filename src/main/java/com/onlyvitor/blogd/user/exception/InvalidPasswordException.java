package com.onlyvitor.blogd.user.exception;

public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException() {
        super("Current password is incorrect");
    }
}
