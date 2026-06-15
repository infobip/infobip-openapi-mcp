package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.PromptTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class PromptIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class StreamableHttpTest extends PromptTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class SseTest extends PromptTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class StatelessTest extends PromptTestBase {}
}
