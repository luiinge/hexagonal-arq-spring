package com.example.commons.app.dto;

import java.time.Instant;

public record ErrorDto(
        String code,
        Object[] args,
        String message,
        Instant timestamp,
        String stackTrace) {

}
