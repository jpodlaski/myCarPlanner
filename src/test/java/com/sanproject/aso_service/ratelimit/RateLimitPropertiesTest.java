package com.sanproject.aso_service.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.LinkedHashMap;
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
        assertThat(loginRule.getRoles()).containsExactly("ANONYMOUS");

        RateLimitProperties.Rule helloRule = bound.getRules().stream()
                .filter(rule -> "/hello".equals(rule.getPath()))
                .findFirst()
                .orElseThrow();
        assertThat(helloRule.getRoles()).containsExactly("ANONYMOUS", "AUTHENTICATED");
    }

    @Test
    void commaSeparatedMethodsAndRolesParse() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setMethods("POST,GET");
        rule.setRoles("ANONYMOUS,AUTHENTICATED");

        assertThat(rule.getMethods()).containsExactly("POST", "GET");
        assertThat(rule.getRoles()).containsExactly("ANONYMOUS", "AUTHENTICATED");
        assertThat(rule.matchesMethod("POST")).isTrue();
        assertThat(rule.matchesMethod("DELETE")).isFalse();
        assertThat(rule.matchesRole(Set.of("ANONYMOUS"))).isTrue();
        assertThat(rule.matchesRole(Set.of("CLIENT"))).isFalse();
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
}
