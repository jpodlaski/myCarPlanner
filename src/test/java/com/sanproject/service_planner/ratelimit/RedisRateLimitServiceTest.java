package com.sanproject.service_planner.ratelimit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimitServiceTest {

    @Test
    void allowsRequestWhenCurrentCountIsBelowLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitProperties properties = new RateLimitProperties();
        RedisRateLimitService service = new RedisRateLimitService(redisTemplate, properties);

        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate:/auth/login:ip:1.2.3.4")),
                anyString(), anyString(), anyString(), anyString())
        ).thenReturn(0L);

        RateLimitDecision decision = service.isAllowed("rate:/auth/login:ip:1.2.3.4", 10, 60);

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getLimit()).isEqualTo(10);
        assertThat(decision.getRemaining()).isEqualTo(9);
        assertThat(decision.getResetEpochSecond()).isPositive();
    }

    @Test
    void blocksRequestWhenCurrentCountReachesLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitProperties properties = new RateLimitProperties();
        RedisRateLimitService service = new RedisRateLimitService(redisTemplate, properties);

        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate:/auth/login:ip:1.2.3.4")),
                anyString(), anyString(), anyString(), anyString())
        ).thenReturn(10L);

        RateLimitDecision decision = service.isAllowed("rate:/auth/login:ip:1.2.3.4", 10, 60);

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRemaining()).isZero();
        assertThat(decision.getLimit()).isEqualTo(10);
    }

    @Test
    void allowsRequestWhenRedisFailsAndFailOpenIsTrue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setFailOpen(true);
        RedisRateLimitService service = new RedisRateLimitService(redisTemplate, properties);

        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate:/auth/login:ip:1.2.3.4")),
                anyString(), anyString(), anyString(), anyString())
        ).thenThrow(new RedisConnectionFailureException("Redis down"));

        RateLimitDecision decision = service.isAllowed("rate:/auth/login:ip:1.2.3.4", 10, 60);

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    void propagatesExceptionWhenRedisFailsAndFailOpenIsFalse() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setFailOpen(false);
        RedisRateLimitService service = new RedisRateLimitService(redisTemplate, properties);

        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate:/auth/login:ip:1.2.3.4")),
                anyString(), anyString(), anyString(), anyString())
        ).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> service.isAllowed("rate:/auth/login:ip:1.2.3.4", 10, 60))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
