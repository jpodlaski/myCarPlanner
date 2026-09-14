package com.sanproject.aso_service.ratelimit;

/**
 * Checks whether a request is within its allowed rate window.
 */
public interface RateLimitService {

    /**
     * Decide whether the caller identified by {@code key} may proceed.
     *
     * @param key         unique bucket key, e.g. "rate:/auth/login:ip:1.2.3.4"
     * @param maxRequests maximum requests allowed in the window
     * @param windowSeconds length of the sliding window in seconds
     * @return decision carrying limit, remaining requests, and reset timestamp
     */
    RateLimitDecision isAllowed(String key, int maxRequests, int windowSeconds);
}
