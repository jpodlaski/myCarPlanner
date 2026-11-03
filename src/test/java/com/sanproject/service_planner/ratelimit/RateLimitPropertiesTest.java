package com.sanproject.service_planner.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    @Test
    void hasSensibleDefaults() {
        RateLimitProperties properties = new RateLimitProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isFailOpen()).isTrue();
        assertThat(properties.getDefaultLimit().getMaxRequests()).isEqualTo(600);
        assertThat(properties.getDefaultLimit().getWindowSeconds()).isEqualTo(60);
        assertThat(properties.getRules()).isEmpty();
    }

    /**
     * Guards against silently mistyped keys: an unknown property binds to nothing and the limiter
     * would quietly keep its hard-coded defaults. This binds the real application.properties.
     */
    @Test
    void shippedConfigurationBindsEveryRateLimitKey() throws Exception {
        Properties all = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        Map<String, Object> rateLimitKeys = new LinkedHashMap<>();
        all.forEach((key, value) -> {
            if (key.toString().startsWith("app.rate-limit.")) {
                rateLimitKeys.put(key.toString(), value);
            }
        });

        assertThat(rateLimitKeys).as("application.properties should configure rate limiting").isNotEmpty();

        RateLimitProperties bound = new Binder(new MapConfigurationPropertySource(rateLimitKeys))
                .bind("app.rate-limit", RateLimitProperties.class)
                .orElseThrow(() -> new AssertionError("app.rate-limit did not bind"));

        assertThat(bound.isEnabled()).isTrue();
        assertThat(bound.getDefaultLimit().getMaxRequests())
                .as("default-limit.max-requests must bind, not fall back to the hard-coded value")
                .isEqualTo(Integer.parseInt(all.getProperty("app.rate-limit.default-limit.max-requests")));
        assertThat(bound.getRules()).isNotEmpty();
        assertThat(bound.getRules()).allSatisfy(rule -> {
            assertThat(rule.getPath()).isNotBlank();
            assertThat(rule.getMaxRequests()).isPositive();
            assertThat(rule.getWindowSeconds()).isPositive();
            assertThat(rule.getMethods()).isNotEmpty();
            assertThat(rule.getRoles()).isNotEmpty();
        });

        RateLimitProperties.Rule loginRule = bound.getRules().stream()
                .filter(rule -> "/auth/login".equals(rule.getPath()))
                .findFirst()
                .orElseThrow();
        assertThat(loginRule.getMethods()).containsExactly("POST");
        assertThat(loginRule.getRoles()).containsExactlyInAnyOrder("ANONYMOUS", "AUTHENTICATED");

        RateLimitProperties.Rule helloRule = bound.getRules().stream()
                .filter(rule -> "/hello".equals(rule.getPath()))
                .findFirst()
                .orElseThrow();
        assertThat(helloRule.getRoles()).containsExactlyInAnyOrder("ANONYMOUS", "AUTHENTICATED");
    }

    /**
     * Every /auth endpoint must have its own strict rule. Without one it falls through to the
     * 600/min default bucket, which contradicts the README's "3–10 requests per minute".
     */
    @Test
    void everyAuthEndpointHasAStrictRule() throws Exception {
        RateLimitProperties bound = loadShippedConfiguration();

        List<RateLimitProperties.Rule> authRules = bound.getRules().stream()
                .filter(rule -> rule.getPath().startsWith("/auth/"))
                .toList();

        assertThat(authRules).extracting(RateLimitProperties.Rule::getPath)
                .contains(
                        "/auth/login",
                        "/auth/employee-login",
                        "/auth/register",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/verify-email",
                        "/auth/resend-verification",
                        "/auth/change-password",
                        "/auth/request-account-deletion",
                        "/auth/confirm-account-deletion");

        assertThat(authRules).allSatisfy(rule ->
                assertThat(rule.getMaxRequests())
                        .as("%s should be strict (3-10 per minute)", rule.getPath())
                        .isBetween(3, 10));
    }

    /**
     * A caller who sends any valid Bearer token is tagged AUTHENTICATED, not ANONYMOUS. Public auth
     * rules must still match them, or the token alone moves them to the 600/min default bucket.
     */
    @Test
    void publicAuthRulesAlsoApplyToCallersWithAToken() throws Exception {
        RateLimitProperties bound = loadShippedConfiguration();
        Set<String> anonymous = Set.of("ANONYMOUS");
        Set<String> signedIn = Set.of("AUTHENTICATED", "CLIENT");

        List<String> publicAuthPaths = List.of(
                "/auth/login",
                "/auth/employee-login",
                "/auth/register",
                "/auth/forgot-password",
                "/auth/reset-password",
                "/auth/verify-email",
                "/auth/resend-verification",
                "/auth/confirm-account-deletion");

        assertThat(publicAuthPaths).allSatisfy(path -> {
            RateLimitProperties.Rule rule = bound.getRules().stream()
                    .filter(r -> path.equals(r.getPath()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no rule for " + path));
            assertThat(rule.matchesRole(anonymous)).as("%s for anonymous callers", path).isTrue();
            assertThat(rule.matchesRole(signedIn)).as("%s for callers with a token", path).isTrue();
        });
    }

    @Test
    void ruleMatchesConfiguredMethodAndRole() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setPath("/auth/login");
        rule.setMethods(Set.of("POST"));
        rule.setRoles(Set.of("ANONYMOUS"));
        rule.setMaxRequests(10);
        rule.setWindowSeconds(60);

        assertThat(rule.matchesMethod("POST")).isTrue();
        assertThat(rule.matchesMethod("GET")).isFalse();
        assertThat(rule.matchesRole(Set.of("ANONYMOUS"))).isTrue();
        assertThat(rule.matchesRole(Set.of("AUTHENTICATED", "CLIENT"))).isFalse();
    }

    @Test
    void wildcardRoleMatchesAnyTag() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setRoles(Set.of("*"));

        assertThat(rule.matchesRole(Set.of("ANONYMOUS"))).isTrue();
        assertThat(rule.matchesRole(Set.of("CLIENT", "AUTHENTICATED"))).isTrue();
    }

    @Test
    void wildcardMethodMatchesAnyHttpMethod() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setMethods(Set.of("*"));

        assertThat(rule.matchesMethod("POST")).isTrue();
        assertThat(rule.matchesMethod("DELETE")).isTrue();
    }

    private static RateLimitProperties loadShippedConfiguration() throws Exception {
        Properties all = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        Map<String, Object> rateLimitKeys = new LinkedHashMap<>();
        all.forEach((key, value) -> {
            if (key.toString().startsWith("app.rate-limit.")) {
                rateLimitKeys.put(key.toString(), value);
            }
        });

        return new Binder(new MapConfigurationPropertySource(rateLimitKeys))
                .bind("app.rate-limit", RateLimitProperties.class)
                .orElseThrow(() -> new AssertionError("app.rate-limit did not bind"));
    }
}
