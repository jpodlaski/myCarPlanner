package com.sanproject.aso_service.ratelimit;

/**
 * Result of a single rate-limit check.
 */
public class RateLimitDecision {

    private final boolean allowed;
    private final long remaining;
    private final long resetEpochSecond;
    private final long limit;

    public RateLimitDecision(boolean allowed, long remaining, long resetEpochSecond, long limit) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.resetEpochSecond = resetEpochSecond;
        this.limit = limit;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getResetEpochSecond() {
        return resetEpochSecond;
    }

    public long getLimit() {
        return limit;
    }
}
