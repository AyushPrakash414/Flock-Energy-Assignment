package com.flockenergy.meterops.exception;

/**
 * Thrown when the upstream Urja portal is unreachable, returns an
 * unexpected status, or produces a malformed response.
 */
public class UpstreamPortalException extends RuntimeException {

    private final int upstreamStatus;

    public UpstreamPortalException(String message) {
        super(message);
        this.upstreamStatus = 0;
    }

    public UpstreamPortalException(String message, int upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public UpstreamPortalException(String message, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = 0;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
