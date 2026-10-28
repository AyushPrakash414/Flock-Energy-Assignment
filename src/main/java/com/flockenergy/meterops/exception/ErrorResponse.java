package com.flockenergy.meterops.exception;

/**
 * Standard error response body returned for all error conditions.
 */
public record ErrorResponse(
        int status,
        String error,
        String message
) {}
