package com.sanproject.service_planner.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Sliding-window rate limiter backed by Redis.
 *
 * <p>Each bucket is a Redis sorted set. Members are unique request ids scored by request time.
 * On every check the script removes entries outside the window, counts what remains, and (if
 * there is capacity) records the current request. The whole operation is atomic.</p>
 *
 * <p>If Redis is unreachable and {@code app.rate-limit.fail-open} is true the request is allowed
 * rather than bringing down the API.</p>
 */
@Service
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimitService implements RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitService.class);

    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local windowMs = tonumber(ARGV[1])
            local nowMs = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            local cutOff = nowMs - windowMs
            redis.call('ZREMRANGEBYSCORE', key, 0, cutOff)
            local current = redis.call('ZCARD', key)
            if current < limit then
                redis.call('ZADD', key, nowMs, member)
            end
            redis.call('EXPIRE', key, math.ceil(windowMs / 1000))
            return current
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.script.setResultType(Long.class);
    }

    @Override
    public RateLimitDecision isAllowed(String key, int maxRequests, int windowSeconds) {
        long nowMs = System.currentTimeMillis();
        long nowSeconds = Instant.ofEpochMilli(nowMs).getEpochSecond();

        try {
            long windowMs = windowSeconds * 1000L;
            String member = nowMs + ":" + UUID.randomUUID();
            List<String> keys = Collections.singletonList(key);

            Long current = redisTemplate.execute(
                    script,
                    keys,
                    String.valueOf(windowMs),
                    String.valueOf(nowMs),
                    String.valueOf(maxRequests),
                    member
            );

            if (current == null) {
                return allowed(maxRequests, nowSeconds + windowSeconds, maxRequests);
            }

            if (current < maxRequests) {
                long remaining = Math.max(0, maxRequests - current - 1);
                return new RateLimitDecision(true, remaining, nowSeconds + windowSeconds, maxRequests);
            }

            return new RateLimitDecision(false, 0, nowSeconds + windowSeconds, maxRequests);
        } catch (Exception ex) {
            logger.warn("Rate-limit Redis call failed for key {}; allowing request. Reason: {}", key, ex.getMessage());
            if (properties.isFailOpen()) {
                return allowed(maxRequests, nowSeconds + windowSeconds, maxRequests);
            }
            throw ex;
        }
    }

    private RateLimitDecision allowed(long limit, long reset, long remaining) {
        return new RateLimitDecision(true, remaining, reset, limit);
    }
}
