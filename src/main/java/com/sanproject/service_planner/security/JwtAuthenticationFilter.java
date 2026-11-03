package com.sanproject.service_planner.security;

import com.sanproject.service_planner.repository.CustomerRepository;
import com.sanproject.service_planner.repository.EmployeeRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a valid Bearer JWT into an AuthUser in the SecurityContext. Tokens are rejected once the
 * account row is gone (client account deletion, or management deleting an employee).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomerRepository customerRepository,
                                   EmployeeRepository employeeRepository) {
        this.jwtService = jwtService;
        this.customerRepository = customerRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    AuthUser user = jwtService.parseToken(token);
                    if (isDeletedAccount(user)) {
                        SecurityContextHolder.clearContext();
                    } else {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (JwtException ignored) {
                    // Invalid token → leave context empty; SecurityFilterChain returns 401 if route needs auth.
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDeletedAccount(AuthUser user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        if ("CLIENT".equals(user.getRole())) {
            return !customerRepository.existsById(user.getId());
        }
        return !employeeRepository.existsById(user.getId());
    }
}
