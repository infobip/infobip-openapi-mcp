package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.NullableUnionTypeTestBase;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class NullableUnionTypeIntegrationTest {

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class NullableUnionTypeHttpTest extends NullableUnionTypeTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class NullableUnionTypeSseTest extends NullableUnionTypeTestBase {}

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class NullableUnionTypeStatelessTest extends NullableUnionTypeTestBase {}
}
