package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.ToolAnnotationConfigOverrideTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class ToolAnnotationConfigOverrideIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = streamable",
                "infobip.openapi.mcp.tools.annotations.get_test.readOnlyHint = false",
                "infobip.openapi.mcp.tools.annotations.get_test.destructiveHint = true",
            })
    class StreamableHttpTest extends ToolAnnotationConfigOverrideTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = sse",
                "infobip.openapi.mcp.tools.annotations.get_test.readOnlyHint = false",
                "infobip.openapi.mcp.tools.annotations.get_test.destructiveHint = true",
            })
    class SseTest extends ToolAnnotationConfigOverrideTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = stateless",
                "infobip.openapi.mcp.tools.annotations.get_test.readOnlyHint = false",
                "infobip.openapi.mcp.tools.annotations.get_test.destructiveHint = true",
            })
    class StatelessTest extends ToolAnnotationConfigOverrideTestBase {}
}
