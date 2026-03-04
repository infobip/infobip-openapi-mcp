package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.AnnotatedExamplesTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class AnnotatedExamplesIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = streamable",
                "infobip.openapi.mcp.tools.examples-mode = ANNOTATED"
            })
    class AnnotatedExamplesHttpTest extends AnnotatedExamplesTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {"spring.ai.mcp.server.protocol = sse", "infobip.openapi.mcp.tools.examples-mode = ANNOTATED"})
    class AnnotatedExamplesSseTest extends AnnotatedExamplesTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = stateless",
                "infobip.openapi.mcp.tools.examples-mode = ANNOTATED"
            })
    class AnnotatedExamplesStatelessTest extends AnnotatedExamplesTestBase {}
}
