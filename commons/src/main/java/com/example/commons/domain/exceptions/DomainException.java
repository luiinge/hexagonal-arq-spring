package com.example.commons.domain.exceptions;

public abstract sealed class DomainException extends RuntimeException permits EntityNotFoundException, ServiceInvocationException, UnexpectedException {

    private final String code;
    private final Object[] args;

    public DomainException(String code, Throwable cause, String message, Object... args) {
        super(String.format(message, args), cause);
        this.args = args;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }

}
