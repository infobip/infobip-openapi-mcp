package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.CustomToolCallFilterTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class CustomToolCallFilterIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class CustomToolCallFilterHttpTest extends CustomToolCallFilterTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class CustomToolCallFilterSseTest extends CustomToolCallFilterTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class CustomToolCallFilterStatelessTest extends CustomToolCallFilterTestBase {}
}
