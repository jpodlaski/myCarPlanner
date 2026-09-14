package com.sanproject.aso_service.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Externalised rate-limit rules. Tuning the numbers does not require a rebuild.
 *
 * <p>The fallback bucket binds from {@code app.rate-limit.default-limit.*} — not
 * {@code app.rate-limit.default.*} — because {@code default} is a Java keyword and cannot be a
 * field name.
 *
 * <p>Example application.properties fragment:
 * <pre>
 * app.rate-limit.enabled=true
 * app.rate-limit.default-limit.max-requests=600
 * app.rate-limit.default-limit.window-seconds=60
 *
 * app.rate-limit.rules[0].path=/auth/login
 * app.rate-limit.rules[0].methods[0]=POST
 * app.rate-limit.rules[0].roles[0]=ANONYMOUS
 * app.rate-limit.rules[0].max-requests=10
 * app.rate-limit.rules[0].window-seconds=60
 * </pre>
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Header to read for the client IP when the API runs behind a trusted reverse proxy. */
    private String trustedHeader;

    /** If Redis is unreachable, allow the request rather than fail the whole call. */
    private boolean failOpen = true;

    private DefaultLimit defaultLimit = new DefaultLimit(600, 60);

    private List<Rule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTrustedHeader() {
        return trustedHeader;
    }

    public void setTrustedHeader(String trustedHeader) {
        this.trustedHeader = trustedHeader;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public DefaultLimit getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(DefaultLimit defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class DefaultLimit {
        private int maxRequests = 600;
        private int windowSeconds = 60;

        public DefaultLimit() {
        }

        public DefaultLimit(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class Rule {
        private String path;
        private Set<String> methods = new LinkedHashSet<>(Set.of("*"));
        private Set<String> roles = new LinkedHashSet<>(Set.of("*"));
        private int maxRequests;
        private int windowSeconds;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Set<String> getMethods() {
            return methods;
        }

        public void setMethods(Set<String> methods) {
            this.methods = methods;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public void setRoles(Set<String> roles) {
            this.roles = roles;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public boolean matchesMethod(String method) {
            return methods.contains("*") || methods.contains(method);
        }

        public boolean matchesRole(Set<String> roleTags) {
            if (roles.contains("*")) {
                return true;
            }
            for (String tag : roleTags) {
                if (roles.contains(tag)) {
                    return true;
                }
            }
            return false;
        }
    }
}
