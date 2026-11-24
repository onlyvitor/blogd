package com.onlyvitor.blogd.user.dto;

public final class PasswordPolicy {

    public static final String REGEX =
            "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])(?=.*[A-Z])(?=.*[a-z]).{8,}$";

    public static final String MESSAGE =
            "Password must be at least 8 characters, including one uppercase letter, one lowercase letter, one number, and one special character";

    private PasswordPolicy() {
    }
}
