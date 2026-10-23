package com.pesachain.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void assignsServerGeneratedCorrelationIdAndContinuesRequest() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chainCalled = new AtomicBoolean();
        FilterChain chain = (incomingRequest, outgoingResponse) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER_NAME))).isNotNull();
        assertThat(chainCalled).isTrue();
    }
}
