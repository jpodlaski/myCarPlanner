package com.sanproject.aso_service.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressExtractorTest {

    @Test
    void fallsBackToRemoteAddressWhenNoTrustedHeaderConfigured() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedHeader(null);
        IpAddressExtractor extractor = new IpAddressExtractor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");

        assertThat(extractor.extract(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void usesTrustedHeaderWhenConfigured() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedHeader("X-Real-Ip");
        IpAddressExtractor extractor = new IpAddressExtractor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-Ip", "1.2.3.4");

        assertThat(extractor.extract(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void usesFirstAddressInForwardedHeaderList() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedHeader("X-Forwarded-For");
        IpAddressExtractor extractor = new IpAddressExtractor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 9.0.0.1");

        assertThat(extractor.extract(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void fallsBackToRemoteAddressWhenTrustedHeaderMissing() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedHeader("X-Real-Ip");
        IpAddressExtractor extractor = new IpAddressExtractor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(extractor.extract(request)).isEqualTo("10.0.0.1");
    }
}
