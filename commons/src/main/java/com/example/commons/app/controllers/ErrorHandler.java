package com.example.commons.app.controllers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import com.example.commons.domain.exceptions.ServiceInvocationException;
import com.example.commons.domain.exceptions.UnexpectedException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.commons.app.dto.ErrorDto;
import com.example.commons.domain.exceptions.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorHandler {

    private static final Object[] EMPTY = {};
    private final Environment environment;

    private String stackTrace(RuntimeException ex) {
        if (!environment.matchesProfiles("dev")) {
            return null;
        }
        StringWriter string = new StringWriter();
        PrintWriter printWriter = new PrintWriter(string);
        ex.printStackTrace(printWriter);
        return string.toString();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDto> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorDto("internal.server.error", EMPTY, ex.getMessage(), Instant.now(), stackTrace(ex)));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFoundException(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorDto(ex.getCode(), ex.getArgs(), ex.getMessage(), Instant.now(), stackTrace(ex)));
    }

    @ExceptionHandler(UnexpectedException.class)
    public ResponseEntity<ErrorDto> handleUnexpectedException(UnexpectedException ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorDto(ex.getCode(), ex.getArgs(), ex.getMessage(), Instant.now(), stackTrace(ex)));
    }

    @ExceptionHandler(ServiceInvocationException.class)
    public ResponseEntity<ErrorDto> handleServiceInvocationException(ServiceInvocationException ex) {
        return ResponseEntity.status(ex.statusCode()).body(ex.error());
    }

}
