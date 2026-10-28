package com.flockenergy.meterops.exception;

/**
 * Thrown when a requested meter ID does not exist in the portal data.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
