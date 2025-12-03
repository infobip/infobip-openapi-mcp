package com.infobip.openapi.mcp.openapi.tool.registration;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test verifying tool registration for Stateless protocol.
 */
@ActiveProfiles("test-tool-registration-stateless")
class StatelessProtocolToolRegistrationTest extends OpenApiTestBase {

    @Test
    void shouldRegisterToolsCorrectlyForStatelessProtocol() {
        // then - verify the correct bean type is registered
        then(applicationContext.containsBean("toolSpecificationsStateless")).isTrue();
        then(applicationContext.containsBean("toolSpecificationsSSE")).isFalse();
        then(applicationContext.containsBean("toolSpecificationsStreamable")).isFalse();
        then(applicationContext.containsBean("toolSpecificationsStdio")).isFalse();

        // and - verify correct number of tools and their names
        @SuppressWarnings("unchecked")
        var toolSpecifications = (List<McpStatelessServerFeatures.SyncToolSpecification>)
                applicationContext.getBean("toolSpecificationsStateless", List.class);

        then(toolSpecifications).hasSize(3);

        var toolNames =
                toolSpecifications.stream().map(spec -> spec.tool().name()).toList();

        then(toolNames).containsExactlyInAnyOrder("get_users", "create_user", "get_user_by_id");
    }
}
