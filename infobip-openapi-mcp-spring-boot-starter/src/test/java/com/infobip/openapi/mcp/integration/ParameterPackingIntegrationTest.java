package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.ParameterPackingTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class ParameterPackingIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class ParameterPackingHttpTest extends ParameterPackingTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class ParameterPackingSseTest extends ParameterPackingTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class ParameterPackingStatelessTest extends ParameterPackingTestBase {}
}
