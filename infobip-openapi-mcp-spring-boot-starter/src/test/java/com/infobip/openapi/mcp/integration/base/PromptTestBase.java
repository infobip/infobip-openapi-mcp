package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PromptTestBase extends IntegrationTestBase {

    private static final String PROMPT_RESOLVE_RESPONSE = """
            {
                "description": "Greet a user",
                "messages": [
                    {"role": "user", "content": "Generate a greeting for Alice."},
                    {"role": "assistant", "content": "Hello Alice, welcome!"}
                ]
            }
            """;

    @DynamicPropertySource
    static void setupPromptSpec(DynamicPropertyRegistry registry) {
        if (staticWireMockServer == null) {
            staticWireMockServer = new WireMockServer(0);
        }
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }
        stubPromptOpenApiSpec();
    }

    @BeforeEach
    void setupPromptStubs() {
        stubPromptOpenApiSpec();
        staticWireMockServer.stubFor(get(urlPathEqualTo("/prompts/greet"))
                .withQueryParam("name", equalTo("Alice"))
                .willReturn(okJson(PROMPT_RESOLVE_RESPONSE)));
        staticWireMockServer.stubFor(get(urlPathEqualTo("/prompts/greet"))
                .withQueryParam("name", absent())
                .willReturn(okJson("""
                        {
                            "description": "Greet a user",
                            "messages": [
                                {"role": "user", "content": "Generate a greeting for {{name}}."}
                            ]
                        }
                        """)));
    }

    private static void stubPromptOpenApiSpec() {
        staticWireMockServer.stubFor(get(urlEqualTo("/openapi.json"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPromptOpenAPISpec())));
    }

    @Test
    void shouldListConfiguredPrompts() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listPrompts();

            // Then
            then(result.prompts()).hasSize(1);
            var prompt = result.prompts().getFirst();
            then(prompt.name()).isEqualTo("greet");
            then(prompt.description()).isEqualTo("Greet a user");
            then(prompt.arguments()).hasSize(1);
            then(prompt.arguments().getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.PromptArgument("name", "User name", true));
        });
    }

    @Test
    void shouldGetPromptWithInterpolatedArguments() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.GetPromptRequest("greet", Map.of("name", "Alice"));

            // When
            var result = givenClient.getPrompt(request);

            // Then
            then(result.description()).isEqualTo("Greet a user");
            then(result.messages()).hasSize(2);

            then(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Generate a greeting for Alice.");

            then(result.messages().get(1).role()).isEqualTo(McpSchema.Role.ASSISTANT);
            then(((McpSchema.TextContent) result.messages().get(1).content()).text())
                    .isEqualTo("Hello Alice, welcome!");
        });
    }

    @Test
    void shouldCallBackendWithoutArgumentsWhenNoneProvided() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.GetPromptRequest("greet", Map.of());

            // When
            var result = givenClient.getPrompt(request);

            // Then
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Generate a greeting for {{name}}.");
        });
    }

    private static String getPromptOpenAPISpec() {
        return """
                {
                    "openapi": "3.1.0",
                    "info": {
                        "title": "Prompt test API",
                        "version": "1.0.0"
                    },
                    "paths": {
                        "/users": {
                            "get": {
                                "operationId": "get-users",
                                "description": "Get all users",
                                "responses": {
                                    "200": {
                                        "description": "OK"
                                    }
                                }
                            }
                        }
                    },
                    "x-mcp-prompts": {
                        "greet": {
                            "description": "Greet a user",
                            "arguments": {
                                "name": {
                                    "description": "User name",
                                    "required": true
                                }
                            },
                            "resolve": {
                                "path": "/prompts/greet",
                                "method": "GET"
                            }
                        }
                    }
                }
                """;
    }
}
