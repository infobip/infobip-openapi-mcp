package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class ResourceTestBase extends IntegrationTestBase {

    @DynamicPropertySource
    static void setupResourceSpec(DynamicPropertyRegistry registry) {
        if (staticWireMockServer == null) {
            staticWireMockServer = new WireMockServer(0);
        }
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }
        stubResourceOpenApiSpec();
    }

    @BeforeEach
    void setupResourceStubs() {
        stubResourceOpenApiSpec();
        staticWireMockServer.stubFor(get(urlPathEqualTo("/resources/greet"))
                .withQueryParam("name", equalTo("Alice"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello Alice, welcome!")));
    }

    private static void stubResourceOpenApiSpec() {
        staticWireMockServer.stubFor(get(urlEqualTo("/openapi.json"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getResourceOpenAPISpec())));
    }

    @Test
    void shouldListAllConfiguredResources() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listResources();

            // Then
            then(result.resources()).hasSize(1);

            var staticResource = result.resources().getFirst();
            then(staticResource.uri()).isEqualTo("res://greet-static");
            then(staticResource.name()).isEqualTo("greet-static");
            then(staticResource.description()).isEqualTo("Greet a user (static content)");
            then(staticResource.mimeType()).isEqualTo("text/plain");
        });
    }

    @Test
    void shouldListAllConfiguredResourceTemplates() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listResourceTemplates();

            // Then
            then(result.resourceTemplates()).hasSize(1);

            var resolvedTemplate = result.resourceTemplates().getFirst();
            then(resolvedTemplate.uriTemplate()).isEqualTo("res://greet/{name}");
            then(resolvedTemplate.name()).isEqualTo("greet-resolved");
            then(resolvedTemplate.description()).isEqualTo("Greet a user (backend resolved)");
        });
    }

    @Test
    void shouldReadInlineResourceWithStaticContent() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.ReadResourceRequest("res://greet-static", null);

            // When
            var result = givenClient.readResource(request);

            // Then
            then(result.contents()).hasSize(1);
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.uri()).isEqualTo("res://greet-static");
            then(contents.mimeType()).isEqualTo("text/plain");
            then(contents.text()).isEqualTo("Hello there, welcome to the platform!");
        });
    }

    @Test
    void shouldReadResolvedResourceWithRawPassthroughContentFromBackend() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.ReadResourceRequest("res://greet/Alice", null);

            // When
            var result = givenClient.readResource(request);

            // Then
            then(result.contents()).hasSize(1);
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.uri()).isEqualTo("res://greet/Alice");
            then(contents.mimeType()).isEqualTo("text/plain");
            then(contents.text()).isEqualTo("Hello Alice, welcome!");
        });
    }

    @Test
    void shouldFailToReadUnknownResourceUri() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.ReadResourceRequest("res://does-not-exist", null);

            // When / Then
            try {
                givenClient.readResource(request);
                then(false).as("Expected exception for unknown resource URI").isTrue();
            } catch (Exception e) {
                then(e.getMessage()).contains("Resource not found");
            }
        });
    }

    private static String getResourceOpenAPISpec() {
        return """
                {
                    "openapi": "3.1.0",
                    "info": {
                        "title": "Resource test API",
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
                    "x-mcp-resources": [
                        {
                            "uri": "res://greet-static",
                            "name": "greet-static",
                            "description": "Greet a user (static content)",
                            "mimeType": "text/plain",
                            "inline": {
                                "text": "Hello there, welcome to the platform!"
                            }
                        },
                        {
                            "uriTemplate": "res://greet/{name}",
                            "name": "greet-resolved",
                            "description": "Greet a user (backend resolved)",
                            "resolve": {
                                "path": "/resources/greet"
                            }
                        }
                    ]
                }
                """;
    }
}
