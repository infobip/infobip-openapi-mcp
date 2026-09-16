package com.infobip.openapi.mcp.resource;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;

import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.tool.naming.OperationIdStrategy;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceRegistryTest {

    @Mock
    private OpenApiRegistry openApiRegistry;

    @Mock
    private ResourceHandler resourceHandler;

    private final OperationIdStrategy namingStrategy = new OperationIdStrategy();
    private final ResourceUriBuilder resourceUriBuilder = new ResourceUriBuilder("api");
    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();

    private ResourceRegistry resourceRegistry;

    @BeforeEach
    void setUp() {
        resourceRegistry = new ResourceRegistry(openApiRegistry, namingStrategy, resourceUriBuilder, resourceHandler);
    }

    @Test
    void shouldReturnEmptyListWhenNoPathsInOpenAPI() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenNoOperationMarkedAsResource() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "description": "Get all users"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldRegisterStaticResourceForParameterLessGetOperation() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus",
                    "summary": "Service status",
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
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).hasSize(1);
        var registered = result.getFirst();
        then(registered.isTemplate()).isFalse();
        then(registered.resource().uri()).isEqualTo("api://status");
        then(registered.resource().name()).isEqualTo("getStatus");
        then(registered.resource().title()).isEqualTo("Service status");
        then(registered.resource().description()).isEqualTo("Returns the current service status");
        then(registered.resource().mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldRegisterResourceTemplateForOperationWithPathParameter() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
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
                    ]
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).hasSize(1);
        var registered = result.getFirst();
        then(registered.isTemplate()).isTrue();
        then(registered.resourceTemplate().uriTemplate()).isEqualTo("api://users/{id}");
        then(registered.resourceTemplate().name()).isEqualTo("getUserById");
        then(registered.resourceTemplate().title()).isEqualTo("getUserById");
        then(registered.resourceTemplate().description()).isEqualTo("Get a user by ID");
        then(registered.resourceTemplate().mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldRegisterResourceTemplateForOperationWithQueryParameter() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "description": "Get all users",
                    "x-mcp-resource": true,
                    "parameters": [
                      {
                        "name": "expand",
                        "in": "query",
                        "schema": { "type": "string" }
                      }
                    ]
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).hasSize(1);
        var registered = result.getFirst();
        then(registered.isTemplate()).isTrue();
        then(registered.resourceTemplate().uriTemplate()).isEqualTo("api://users{?expand}");
    }

    @Test
    void shouldThrowResourceRegistrationExceptionWhenNonGetOperationMarkedAsResource() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "post": {
                    "operationId": "createUser",
                    "description": "Create a new user",
                    "x-mcp-resource": true
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When & Then
        thenThrownBy(() -> resourceRegistry.getResources())
                .isInstanceOf(ResourceRegistrationException.class)
                .hasMessageContaining("Unable to register resource for operation: POST /users")
                .hasMessageContaining("Only GET operations can be marked");
    }

    @Test
    void shouldThrowResourceRegistrationExceptionWhenOperationIdIsMissing() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "description": "Returns the current service status",
                    "x-mcp-resource": true
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When & Then
        thenThrownBy(() -> resourceRegistry.getResources())
                .isInstanceOf(ResourceRegistrationException.class)
                .hasMessageContaining("Unable to register resource for operation: GET /status")
                .hasMessageContaining("Error determining resource name")
                .hasCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFallBackToDefaultMimeTypeWhenNoSuccessResponseContentDeclared() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus",
                    "x-mcp-resource": true
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).hasSize(1);
        then(result.getFirst().resource().mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldDeriveMimeTypeFromSuccessResponseContent() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/report": {
                  "get": {
                    "operationId": "getReport",
                    "x-mcp-resource": true,
                    "responses": {
                      "200": {
                        "description": "OK",
                        "content": {
                          "text/csv": { "schema": { "type": "string" } }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = resourceRegistry.getResources();

        // Then
        then(result).hasSize(1);
        then(result.getFirst().resource().mimeType()).isEqualTo("text/csv");
    }

    @Nested
    class Cache {

        @Test
        void shouldStartEmpty() {
            then(resourceRegistry.getRegisteredResourcesCache()).isEmpty();
        }

        @Test
        void shouldBePopulatedAfterGetResourcesIsCalled() {
            // Given
            var openApi = parseOpenAPI("""
                {
                  "openapi": "3.1.0",
                  "info": { "title": "Test API", "version": "1.0.0" },
                  "paths": {
                    "/status": {
                      "get": {
                        "operationId": "getStatus",
                        "x-mcp-resource": true
                      }
                    }
                  }
                }
                """);
            given(openApiRegistry.openApi()).willReturn(openApi);

            // When
            resourceRegistry.getResources();

            // Then
            then(resourceRegistry.getRegisteredResourcesCache()).hasSize(1);
            then(resourceRegistry.getRegisteredResourcesCache().getFirst().name())
                    .isEqualTo("getStatus");
        }
    }

    private OpenAPI parseOpenAPI(String jsonSpec) {
        return parser.readContents(jsonSpec).getOpenAPI();
    }
}
