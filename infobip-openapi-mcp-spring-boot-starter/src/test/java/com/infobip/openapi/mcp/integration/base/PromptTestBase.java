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
                "description": "Greet a user via backend",
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
        staticWireMockServer.stubFor(get(urlPathEqualTo("/prompts/greet-resolved"))
                .withQueryParam("name", equalTo("Alice"))
                .willReturn(okJson(PROMPT_RESOLVE_RESPONSE)));
        staticWireMockServer.stubFor(get(urlPathEqualTo("/prompts/greet-resolved"))
                .withQueryParam("name", absent())
                .willReturn(okJson("""
                        {
                            "description": "Greet a user via backend",
                            "messages": [
                                {"role": "user", "content": "Generate a greeting."}
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
    void shouldListAllConfiguredPrompts() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listPrompts();

            // Then
            then(result.prompts()).hasSize(2);

            var staticPrompt = result.prompts().stream()
                    .filter(p -> p.name().equals("greet-static"))
                    .findFirst()
                    .orElseThrow();
            then(staticPrompt.description()).isEqualTo("Greet a user (static template)");
            then(staticPrompt.arguments()).hasSize(1);
            then(staticPrompt.arguments().getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.PromptArgument("name", "User name", true));

            var resolvedPrompt = result.prompts().stream()
                    .filter(p -> p.name().equals("greet-resolved"))
                    .findFirst()
                    .orElseThrow();
            then(resolvedPrompt.description()).isEqualTo("Greet a user (backend resolved)");
        });
    }

    @Test
    void shouldGetInlinePromptWithRenderedTemplate() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.GetPromptRequest("greet-static", Map.of("name", "Alice"));

            // When
            var result = givenClient.getPrompt(request);

            // Then
            then(result.description()).isEqualTo("Greet a user (static template)");
            then(result.messages()).hasSize(2);

            then(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("You are about to greet Alice.");

            then(result.messages().get(1).role()).isEqualTo(McpSchema.Role.ASSISTANT);
            then(((McpSchema.TextContent) result.messages().get(1).content()).text())
                    .isEqualTo("Hello Alice, welcome to the platform!");
        });
    }

    @Test
    void shouldGetResolvedPromptFromBackend() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.GetPromptRequest("greet-resolved", Map.of("name", "Alice"));

            // When
            var result = givenClient.getPrompt(request);

            // Then
            then(result.description()).isEqualTo("Greet a user via backend");
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
    void shouldRenderInlinePromptWithMissingRequiredArgAsError() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.GetPromptRequest("greet-static", Map.of());

            // When / Then — missing required arg should cause an error
            try {
                givenClient.getPrompt(request);
                then(false).as("Expected exception for missing required arg").isTrue();
            } catch (Exception e) {
                then(e.getMessage()).contains("name");
            }
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
                    "x-mcp-prompts": [
                        {
                            "name": "greet-static",
                            "description": "Greet a user (static template)",
                            "arguments": [
                                {
                                    "name": "name",
                                    "description": "User name",
                                    "required": true
                                }
                            ],
                            "messages": [
                                {
                                    "role": "user",
                                    "content": "You are about to greet {{name}}."
                                },
                                {
                                    "role": "assistant",
                                    "content": "Hello {{name}}, welcome to the platform!"
                                }
                            ]
                        },
                        {
                            "name": "greet-resolved",
                            "description": "Greet a user (backend resolved)",
                            "arguments": [
                                {
                                    "name": "name",
                                    "description": "User name",
                                    "required": true
                                }
                            ],
                            "resolve": {
                                "path": "/prompts/greet-resolved"
                            }
                        }
                    ]
                }
                """;
    }
}
