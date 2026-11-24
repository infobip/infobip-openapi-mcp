package com.infobip.openapi.mcp.auth.web;

import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class BufferingMcpResponseTest {

    @Test
    void shouldCaptureStatusContentTypeAndBodyWrittenToOutputStream() throws Exception {
        var realResponse = new MockHttpServletResponse();
        var buffered = new BufferingMcpResponse(realResponse);

        buffered.setStatus(202);
        buffered.setContentType("application/json");
        buffered.getOutputStream().write("{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        then(buffered.getStatus()).isEqualTo(202);
        then(buffered.getContentType()).isEqualTo("application/json");
        then(new String(buffered.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
    }

    @Test
    void shouldCaptureBodyWrittenToWriter() throws Exception {
        var buffered = new BufferingMcpResponse(new MockHttpServletResponse());

        buffered.getWriter().write("hello");

        then(new String(buffered.getBody(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void shouldKeepRealResponseUncommittedWhenFlushed() throws IOException {
        var realResponse = new MockHttpServletResponse();
        var buffered = new BufferingMcpResponse(realResponse);

        buffered.getOutputStream().write("payload".getBytes(StandardCharsets.UTF_8));
        buffered.getOutputStream().flush();
        buffered.flushBuffer();

        then(realResponse.isCommitted()).isFalse();
        then(realResponse.getContentAsByteArray()).isEmpty();
    }

    @Test
    void shouldCaptureHeadersWithSetReplacingAndAddAppending() {
        var buffered = new BufferingMcpResponse(new MockHttpServletResponse());

        buffered.setHeader("X-Foo", "one");
        buffered.setHeader("X-Foo", "two");
        buffered.addHeader("X-Bar", "first");
        buffered.addHeader("X-Bar", "second");

        then(buffered.getHeadersSnapshot().get("X-Foo")).containsExactly("two");
        then(buffered.getHeadersSnapshot().get("X-Bar")).containsExactly("first", "second");
        then(buffered.getHeader("X-Bar")).isEqualTo("first");
    }
}
