package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

public abstract class ToolAnnotationConfigOverrideTestBase extends IntegrationTestBase {

    @Test
    void shouldOverrideAnnotationsViaConfigProperties() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/base.json");

            // When
            var tools = givenClient.listTools().tools();

            // Then
            then(tools).hasSize(1);
            then(tools.getFirst().name()).isEqualTo("get_test");
            then(tools.getFirst().annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, false, true, true, true, true));
        });
    }
}
