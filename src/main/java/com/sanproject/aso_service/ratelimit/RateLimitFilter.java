package com.sanproject.aso_service.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanproject.aso_service.domain.EmployeeRole;
import com.sanproject.aso_service.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces per-IP and per-user rate limits for every HTTP request.
 *
 * <p>The filter runs after the JWT filter so it can inspect the authenticated principal. It finds
 * the first matching rule (path + role) and asks {@link RateLimitService} whether the request is
 * within its sliding window. Rate-limit headers are attached to every response; blocked requests
 * are returned as {@code 429 Too Many Requests}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET = "RateLimit-Reset";
    private static final String RETRY_AFTER = "Retry-After";

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final IpAddressExtractor ipExtractor;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitProperties properties,
                           RateLimitService rateLimitService,
                           IpAddressExtractor ipExtractor,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.ipExtractor = ipExtractor;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        Set<String> roleTags = resolveRoleTags();

        RateLimitProperties.Rule rule = findMatchingRule(path, method, roleTags);
        if (rule == null && properties.getDefaultLimit() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxRequests;
        int windowSeconds;
        String rulePath;
        if (rule != null) {
            maxRequests = rule.getMaxRequests();
            windowSeconds = rule.getWindowSeconds();
            rulePath = rule.getPath();
        } else {
            maxRequests = properties.getDefaultLimit().getMaxRequests();
            windowSeconds = properties.getDefaultLimit().getWindowSeconds();
            rulePath = "default";
        }

        String identifier = resolveIdentifier(request, roleTags);
        String bucketKey = "rate:" + rulePath + ":" + identifier;

        RateLimitDecision decision = rateLimitService.isAllowed(bucketKey, maxRequests, windowSeconds);
        applyHeaders(response, decision);

        if (!decision.isAllowed()) {
            long retryAfter = Math.max(1, decision.getResetEpochSecond() - Instant.now().getEpochSecond());
            response.setHeader(RETRY_AFTER, String.valueOf(retryAfter));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String body = objectMapper.writeValueAsString(
                    Map.of("message", "Rate limit exceeded. Retry after " + retryAfter + " seconds."));
            response.getWriter().write(body);
            logger.warn("Rate limit exceeded: method={}, path={}, identifier={}, bucket={}", method, path, identifier, bucketKey);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Set<String> resolveRoleTags() {
        Set<String> tags = new LinkedHashSet<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof AuthUser user) {
            tags.add("AUTHENTICATED");
            tags.add(user.getRole());
            try {
                EmployeeRole role = EmployeeRole.valueOf(user.getRole());
                if (role.canManageWorkers()) {
                    tags.add("MANAGEMENT");
                }
                if (role.isWorkshopStaff()) {
                    tags.add("WORKSHOP");
                }
            } catch (IllegalArgumentException ignored) {
                // Unknown role string stays with the literal role tag only.
            }
        } else {
            tags.add("ANONYMOUS");
        }
        return tags;
    }

    private String resolveIdentifier(HttpServletRequest request, Set<String> roleTags) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof AuthUser user && !roleTags.contains("ANONYMOUS")) {
            return "user:" + user.getId() + ":role:" + user.getRole();
        }
        return "ip:" + ipExtractor.extract(request);
    }

    private RateLimitProperties.Rule findMatchingRule(String path, String method, Set<String> roleTags) {
        List<RateLimitProperties.Rule> rules = properties.getRules();
        if (rules == null) {
            return null;
        }
        for (RateLimitProperties.Rule rule : rules) {
            if (rule.getPath() == null) {
                continue;
            }
            if (pathMatcher.match(rule.getPath(), path) && rule.matchesMethod(method) && rule.matchesRole(roleTags)) {
                return rule;
            }
        }
        return null;
    }

    private void applyHeaders(HttpServletResponse response, RateLimitDecision decision) {
        if (response.isCommitted()) {
            return;
        }
        response.setHeader(RATE_LIMIT_LIMIT, String.valueOf(decision.getLimit()));
        response.setHeader(RATE_LIMIT_REMAINING, String.valueOf(decision.getRemaining()));
        response.setHeader(RATE_LIMIT_RESET, String.valueOf(decision.getResetEpochSecond()));
    }

}
