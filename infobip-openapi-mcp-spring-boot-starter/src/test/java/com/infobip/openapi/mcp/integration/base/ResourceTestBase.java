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
        staticWireMockServer.stubFor(get(urlPathEqualTo("/status")).willReturn(okJson("{\"status\": \"UP\"}")));
        staticWireMockServer.stubFor(
                get(urlPathEqualTo("/users/42")).willReturn(okJson("{\"id\": \"42\", \"name\": \"Alice\"}")));
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
    void shouldListStaticResources() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listResources();

            // Then
            then(result.resources()).hasSize(1);
            var resource = result.resources().getFirst();
            then(resource.name()).isEqualTo("getstatus");
            then(resource.uri()).isEqualTo("api://status");
            then(resource.description()).isEqualTo("Returns the current service status");
            then(resource.mimeType()).isEqualTo("application/json");
        });
    }

    @Test
    void shouldListResourceTemplates() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listResourceTemplates();

            // Then
            then(result.resourceTemplates()).hasSize(1);
            var resourceTemplate = result.resourceTemplates().getFirst();
            then(resourceTemplate.name()).isEqualTo("getuserbyid");
            then(resourceTemplate.uriTemplate()).isEqualTo("api://users/{id}");
        });
    }

    @Test
    void shouldReadStaticResource() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.ReadResourceRequest("api://status");

            // When
            var result = givenClient.readResource(request);

            // Then
            then(result.contents()).hasSize(1);
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.uri()).isEqualTo("api://status");
            then(contents.mimeType()).isEqualTo("application/json");
            then(contents.text()).isEqualTo("{\"status\": \"UP\"}");
        });
    }

    @Test
    void shouldReadResourceTemplateWithPathVariableSubstitution() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.ReadResourceRequest("api://users/42");

            // When
            var result = givenClient.readResource(request);

            // Then
            then(result.contents()).hasSize(1);
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.uri()).isEqualTo("api://users/42");
            then(contents.text()).isEqualTo("{\"id\": \"42\", \"name\": \"Alice\"}");
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
                        "/status": {
                            "get": {
                                "operationId": "getStatus",
                                "description": "Returns the current service status",
                                "x-mcp-resource": true,
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "application/json": { "schema": { "type": "object" } }
                                        }
                                    }
                                }
                            }
                        },
                        "/users/{id}": {
                            "get": {
                                "operationId": "getUserById",
                                "description": "Get a user by ID",
                                "x-mcp-resource": true,
                                "parameters": [
                                    {
                                        "name": "id",
                                        "in": "path",
                                        "required": true,
                                        "schema": { "type": "string" }
                                    }
                                ],
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "application/json": { "schema": { "type": "object" } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """;
    }
}
