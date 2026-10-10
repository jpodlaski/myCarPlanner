package com.sanproject.service_planner.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the client IP address, honouring a trusted reverse-proxy header when configured.
 *
 * <p>Never blindly trust {@code X-Forwarded-For} in production; configure the exact header your
 * trusted proxy sets (often {@code X-Real-Ip}) via {@code app.rate-limit.trusted-header}.</p>
 */
@Component
public class IpAddressExtractor {

    private final RateLimitProperties properties;

    public IpAddressExtractor(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String extract(HttpServletRequest request) {
        String trustedHeader = properties.getTrustedHeader();
        if (StringUtils.hasText(trustedHeader)) {
            String value = request.getHeader(trustedHeader);
            if (StringUtils.hasText(value)) {
                String first = value.split(",")[0].trim();
                if (StringUtils.hasText(first)) {
                    return first;
                }
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
}
