package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "infobip.openapi.mcp.tools.mock = true")
public abstract class MockerTestBase extends IntegrationTestBase {

    @Test
    void shouldReceiveMockResultFromGetUsersTool() {
        withInitializedMcpClient(givenClient -> {
            // When
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_users")
                    .arguments(Map.of())
                    .build());

            // Then
            then(actualResponse).isNotNull();
            then(actualResponse.isError()).isFalse();
            then(actualResponse.content()).hasSize(1).hasOnlyElementsOfType(McpSchema.TextContent.class);
            var actualJson = ((McpSchema.TextContent) actualResponse.content().getFirst()).text();
            try {
                assertEquals("""
                        [
                            {
                                "id": 123,
                                "name": "Jane Doe"
                            },
                            {
                                "id": 456,
                                "name": "John Doe"
                            }
                        ]""", actualJson, true);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void shouldReceiveMockResultFromGetOneUser() {
        withInitializedMcpClient(givenClient -> {
            // When
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_user_by_id")
                    .arguments(Map.of("userId", "123"))
                    .build());

            // Then
            then(actualResponse).isNotNull();
            then(actualResponse.isError()).isFalse();
            then(actualResponse.content()).hasSize(1).hasOnlyElementsOfType(McpSchema.TextContent.class);
            var actualJson = ((McpSchema.TextContent) actualResponse.content().getFirst()).text();
            try {
                assertEquals("""
                        {
                            "id": 123,
                            "name": "Jane Doe"
                        }""", actualJson, true);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
