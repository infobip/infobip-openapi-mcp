package com.infobip.openapi.mcp.openapi.tool.registration;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test verifying tool registration for Stdio protocol.
 */
@ActiveProfiles("test-tool-registration-stdio")
class StdioProtocolToolRegistrationTest extends OpenApiTestBase {

    @Test
    void shouldRegisterToolsCorrectlyForStdioProtocol() {
        // then - verify the correct bean type is registered
        then(applicationContext.containsBean("toolSpecificationsStdio")).isTrue();
        then(applicationContext.containsBean("toolSpecificationsSSE")).isFalse();
        then(applicationContext.containsBean("toolSpecificationsStreamable")).isFalse();
        then(applicationContext.containsBean("toolSpecificationsStateless")).isFalse();

        // and - verify correct number of tools and their names
        @SuppressWarnings("unchecked")
        var toolSpecifications = (List<McpServerFeatures.SyncToolSpecification>)
                applicationContext.getBean("toolSpecificationsStdio", List.class);

        then(toolSpecifications).hasSize(3);

        var toolNames =
                toolSpecifications.stream().map(spec -> spec.tool().name()).toList();

        then(toolNames).containsExactlyInAnyOrder("get_users", "create_user", "get_user_by_id");
    }
}
