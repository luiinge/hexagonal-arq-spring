package com.example.commons.domain.exceptions;

public final class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String message, Object... args) {
        super("entity.not.found", null, message, args);
    }

}
