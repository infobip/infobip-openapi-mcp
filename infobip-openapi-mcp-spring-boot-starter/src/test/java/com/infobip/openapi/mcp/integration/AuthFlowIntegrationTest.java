package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.AuthFlowTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class AuthFlowIntegrationTest {

    @Nested
    @ActiveProfiles({"integration", "integration-security"})
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class AuthFlowHttpTest extends AuthFlowTestBase {}

    @Nested
    @ActiveProfiles({"integration", "integration-security"})
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class AuthFlowSseTest extends AuthFlowTestBase {}

    @Nested
    @ActiveProfiles({"integration", "integration-security"})
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class AuthFlowStatelessTest extends AuthFlowTestBase {}
}
