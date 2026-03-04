package com.example.commons.domain.exceptions;

import com.example.commons.app.dto.ErrorDto;

public final class ServiceInvocationException extends DomainException {

    private final ErrorDto error;
    private final int statusCode;

    public ServiceInvocationException(int statusCode, ErrorDto error) {
        super("service.invocation.error", null, error.message());
        this.error = error;
        this.statusCode = statusCode;
    }

    public ErrorDto error() {
        return error;
    }

    public int statusCode() {
        return statusCode;
    }

}
