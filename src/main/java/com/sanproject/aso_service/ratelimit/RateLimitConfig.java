package com.sanproject.aso_service.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables rate-limit configuration properties and keeps {@link RateLimitFilter} out of the plain
 * servlet filter chain.
 *
 * <p>Spring Boot registers every {@code Filter} bean with the servlet container by default. The
 * filter must only run inside Spring Security's chain, where the JWT principal is already
 * resolved, so the auto-registration is explicitly disabled here. Without this, the servlet-level
 * copy runs with an empty SecurityContext and {@code OncePerRequestFilter} then skips the copy
 * inside the security chain, making every caller look anonymous.</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
