package com.sanproject.aso_service.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanproject.aso_service.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);

    private RateLimitFilter filter;
    private RateLimitProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reset(rateLimitService);
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.getDefaultLimit().setMaxRequests(600);
        properties.getDefaultLimit().setWindowSeconds(60);

        RateLimitProperties.Rule clientBookingRule = new RateLimitProperties.Rule();
        clientBookingRule.setPath("/bookings");
        clientBookingRule.setMethods(Set.of("POST"));
        clientBookingRule.setRoles(Set.of("CLIENT"));
        clientBookingRule.setMaxRequests(10);
        clientBookingRule.setWindowSeconds(60);
        properties.setRules(List.of(clientBookingRule));

        objectMapper = new ObjectMapper();
        filter = new RateLimitFilter(properties, rateLimitService, new IpAddressExtractor(properties), objectMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsOptionsRequestsWithoutCallingService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimitService, never()).isAllowed(anyString(), anyInt(), anyInt());
    }

    @Test
    void usesIpKeyForAnonymousRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimitService.isAllowed("rate:default:ip:1.2.3.4", 600, 60))
                .thenReturn(new RateLimitDecision(true, 599, Instant.now().getEpochSecond() + 60, 600));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("600");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("599");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void usesUserKeyForAuthenticatedClientRequest() throws Exception {
        AuthUser client = new AuthUser(42L, "CLIENT", "Test Client");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(client, null, client.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bookings");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimitService.isAllowed("rate:/bookings:user:42:role:CLIENT", 10, 60))
                .thenReturn(new RateLimitDecision(true, 9, Instant.now().getEpochSecond() + 60, 10));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("9");
    }

    @Test
    void returns429AndStopsChainWhenBlocked() throws Exception {
        AuthUser client = new AuthUser(7L, "CLIENT", "Blocked Client");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(client, null, client.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bookings");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        long reset = Instant.now().getEpochSecond() + 60;
        when(rateLimitService.isAllowed("rate:/bookings:user:7:role:CLIENT", 10, 60))
                .thenReturn(new RateLimitDecision(false, 0, reset, 10));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void passesCorrectParametersToServiceForDefaultRule() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles/catalog");
        request.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt()))
                .thenReturn(new RateLimitDecision(true, 599, Instant.now().getEpochSecond() + 60, 600));

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> windowCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), limitCaptor.capture(), windowCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("rate:default:ip:9.9.9.9");
        assertThat(limitCaptor.getValue()).isEqualTo(600);
        assertThat(windowCaptor.getValue()).isEqualTo(60);
    }

    /**
     * The servlet-level copy of the filter would see an empty SecurityContext and, via
     * OncePerRequestFilter, suppress the copy inside the security chain. RateLimitConfig disables
     * that registration; this pins the behaviour the filter relies on.
     */
    @Test
    void doesNotRunTwiceForTheSameRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt()))
                .thenReturn(new RateLimitDecision(true, 599, Instant.now().getEpochSecond() + 60, 600));

        filter.doFilter(request, response, new MockFilterChain());
        filter.doFilter(request, response, new MockFilterChain());

        verify(rateLimitService, times(1)).isAllowed(anyString(), anyInt(), anyInt());
    }
}
