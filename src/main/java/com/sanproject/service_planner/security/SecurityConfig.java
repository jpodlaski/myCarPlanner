package com.sanproject.service_planner.security;

import com.sanproject.service_planner.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. This chain only separates public from authenticated routes;
 * role checks (e.g. only clients create bookings) happen in AuthSupport.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectProvider<RateLimitFilter> rateLimitFilterProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectProvider<RateLimitFilter> rateLimitFilterProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilterProvider = rateLimitFilterProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // No cookie sessions to protect: every request authenticates with a Bearer token.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: account flows and the catalog, needed before anyone is signed in.
                        .requestMatchers(
                                "/auth/login",
                                "/auth/employee-login",
                                "/auth/register",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/auth/confirm-account-deletion",
                                "/hello")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/vehicles/catalog").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .anonymous(anon -> anon.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        rateLimitFilterProvider.ifAvailable(rateLimitFilter ->
                http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class));

        return http.build();
    }
}
