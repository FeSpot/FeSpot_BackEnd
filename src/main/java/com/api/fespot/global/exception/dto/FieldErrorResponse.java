package com.api.fespot.global.exception.dto;

public record FieldErrorResponse(
        String field,
        String rejectedValue,
        String message
) {
}
