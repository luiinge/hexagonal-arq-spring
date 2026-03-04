package com.example.commons.domain.exceptions;

public final class UnexpectedException extends DomainException {

    public UnexpectedException(String message, Object... args) {
        super("unexpected.error", null, message, args);
    }

}
