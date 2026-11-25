package com.onlyvitor.blogd.post.exception;

public class PostDeletionNotAllowedException extends RuntimeException {

    public PostDeletionNotAllowedException() {
        super("Post can only be deleted when most of its authors accept it (ties are decided by the main author)");
    }
}
