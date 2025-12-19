package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.MockerTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class MockerIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class MockerHttpTest extends MockerTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class MockerSseTest extends MockerTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class MockerStatelessTest extends MockerTestBase {}
}
