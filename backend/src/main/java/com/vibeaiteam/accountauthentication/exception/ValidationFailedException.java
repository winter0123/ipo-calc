package com.vibeaiteam.accountauthentication.exception;

public class ValidationFailedException extends RuntimeException {

    private final String field;
    private final String message;

    public ValidationFailedException(String field, String message) {
        super(message);
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }
}