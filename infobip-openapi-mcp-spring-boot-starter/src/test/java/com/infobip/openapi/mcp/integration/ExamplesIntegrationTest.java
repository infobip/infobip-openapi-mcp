package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.ExamplesTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class ExamplesIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {"spring.ai.mcp.server.protocol = streamable", "infobip.openapi.mcp.tools.examples-mode = ALL"
            })
    class ExamplesHttpTest extends ExamplesTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {"spring.ai.mcp.server.protocol = sse", "infobip.openapi.mcp.tools.examples-mode = ALL"})
    class ExamplesSseTest extends ExamplesTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {"spring.ai.mcp.server.protocol = stateless", "infobip.openapi.mcp.tools.examples-mode = ALL"})
    class ExamplesStatelessTest extends ExamplesTestBase {}
}
