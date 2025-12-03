package com.infobip.openapi.mcp.integration;

import com.infobip.openapi.mcp.integration.base.InitializationTestBase;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

public class InitializationTest {

    private static final McpSchema.InitializeResult INIT_RESULT_FROM_OPEN_API = new McpSchema.InitializeResult(
            null,
            null,
            new McpSchema.Implementation("User management", null, "1.2.7"),
            "Fetch **paginated** list of multiple users or one by one.");

    private static final McpSchema.InitializeResult INIT_RESULT_FROM_ENVIRONMENT = new McpSchema.InitializeResult(
            null, null, new McpSchema.Implementation("Name override", null, "1.0.0"), "Instructions override");

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = streamable")
    class InitializationHttpTest extends InitializationTestBase {
        @Override
        protected McpSchema.InitializeResult expectedResult() {
            return INIT_RESULT_FROM_OPEN_API;
        }
    }

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = sse")
    class InitializationSseTest extends InitializationTestBase {
        @Override
        protected McpSchema.InitializeResult expectedResult() {
            return INIT_RESULT_FROM_OPEN_API;
        }
    }

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(properties = "spring.ai.mcp.server.protocol = stateless")
    class InitializationStatelessTest extends InitializationTestBase {
        @Override
        protected McpSchema.InitializeResult expectedResult() {
            return INIT_RESULT_FROM_OPEN_API;
        }
    }

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = streamable",
                "spring.ai.mcp.server.name = Name override",
                "spring.ai.mcp.server.version = 1.0.0",
                "spring.ai.mcp.server.instructions = Instructions override",
            })
    class InitializationOverrideHttpTest extends InitializationTestBase {
        @Override
        protected McpSchema.InitializeResult expectedResult() {
            return INIT_RESULT_FROM_ENVIRONMENT;
        }
    }

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = sse",
                "spring.ai.mcp.server.name = Name override",
                "spring.ai.mcp.server.version = 1.0.0",
                "spring.ai.mcp.server.instructions = Instructions override",
            })
    class InitializationOverrideSseTest extends InitializationTestBase {
        @Override
        protected McpSchema.InitializeResult expectedResult() {
            return INIT_RESULT_FROM_ENVIRONMENT;
        }
    }

    @Nested
    @ActiveProfiles("integration")
    @TestPropertySource(
            properties = {
                "spring.ai.mcp.server.protocol = stateless",
                "spring.ai.mcp.server.name = Name override",
                "spring.ai.mcp.server.version = 1.0.0",
                "spring.ai.mcp.server.instructions = Instructions override",
            })
    class InitializationOverrideStatelessTest extends InitializationTestBase {
        @Override
        protected McpSchema.InitializeResult expectedResult() {
            return INIT_RESULT_FROM_ENVIRONMENT;
        }
    }
}
