package com.onlyvitor.blogd.post.exception;

public class InvalidRelatedPostException extends RuntimeException {

    public InvalidRelatedPostException() {
        super("A post cannot be related to itself");
    }
}
