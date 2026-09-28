package com.infobip.openapi.mcp.resource;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResourceUriBuilderTest {

    private final ResourceUriBuilder resourceUriBuilder = new ResourceUriBuilder("api");
    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();

    @Test
    void shouldBuildStaticUriForParameterLessOperation() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus"
                  }
                }
              }
            }
            """);

        // When & Then
        then(resourceUriBuilder.isTemplate(fullOperation)).isFalse();
        then(resourceUriBuilder.build(fullOperation)).isEqualTo("api://status");
    }

    @Test
    void shouldBuildUriTemplateForPathParameter() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users/{id}": {
                  "get": {
                    "operationId": "getUserById",
                    "parameters": [
                      { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);

        // When & Then
        then(resourceUriBuilder.isTemplate(fullOperation)).isTrue();
        then(resourceUriBuilder.build(fullOperation)).isEqualTo("api://users/{id}");
    }

    @Test
    void shouldBuildUriTemplateWithQueryExpansionForQueryParameters() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "parameters": [
                      { "name": "limit", "in": "query", "schema": { "type": "string" } },
                      { "name": "expand", "in": "query", "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);

        // When & Then
        then(resourceUriBuilder.isTemplate(fullOperation)).isTrue();
        then(resourceUriBuilder.build(fullOperation)).isEqualTo("api://users{?limit,expand}");
    }

    @Test
    void shouldBuildUriTemplateWithPathAndQueryParameters() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users/{id}": {
                  "get": {
                    "operationId": "getUserById",
                    "parameters": [
                      { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } },
                      { "name": "expand", "in": "query", "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);

        // When & Then
        then(resourceUriBuilder.build(fullOperation)).isEqualTo("api://users/{id}{?expand}");
    }

    @Test
    void shouldMatchPathVariablesFromConcreteUri() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users/{id}": {
                  "get": {
                    "operationId": "getUserById",
                    "parameters": [
                      { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);

        // When
        var pathVariables = resourceUriBuilder.matchPathVariables(fullOperation, "api://users/123");

        // Then
        then(pathVariables).containsExactly(java.util.Map.entry("id", "123"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # resourceUri                     | expectedValue
            api://users/John%20Doe            | John Doe
            api://users/a+b                   | a+b
            api://users/%2B385                | +385
            api://users/%7Bid%7D               | {id}
            """)
    void shouldPercentDecodePathVariableValuesPreservingLiteralPlus(String resourceUri, String expectedValue) {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users/{id}": {
                  "get": {
                    "operationId": "getUserById",
                    "parameters": [
                      { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);

        // When
        var pathVariables = resourceUriBuilder.matchPathVariables(fullOperation, resourceUri);

        // Then
        then(pathVariables).containsExactly(java.util.Map.entry("id", expectedValue));
    }

    @Test
    void shouldReturnEmptyMapWhenNoPathParametersDeclared() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus"
                  }
                }
              }
            }
            """);

        // When
        var pathVariables = resourceUriBuilder.matchPathVariables(fullOperation, "api://status");

        // Then
        then(pathVariables).isEmpty();
    }

    @Test
    void shouldExtractQueryParametersFromConcreteUri() {
        // Given
        var fullOperation = usersOperationWithQueryParameters("limit", "expand");

        // When
        var queryParameters =
                resourceUriBuilder.extractQueryParameters(fullOperation, "api://users?limit=10&expand=profile");

        // Then
        then(queryParameters)
                .containsExactly(java.util.Map.entry("limit", "10"), java.util.Map.entry("expand", "profile"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # resourceUri                   | expectedValue
            api://users?name=John%20Doe     | John Doe
            api://users?name=a+b            | a+b
            api://users?name=%2B385         | +385
            api://users?name=%7Bid%7D       | {id}
            """)
    void shouldPercentDecodeQueryParameterValuesPreservingLiteralPlus(String resourceUri, String expectedValue) {
        // Given
        var fullOperation = usersOperationWithQueryParameters("name");

        // When
        var queryParameters = resourceUriBuilder.extractQueryParameters(fullOperation, resourceUri);

        // Then
        then(queryParameters).containsExactly(java.util.Map.entry("name", expectedValue));
    }

    @Test
    void shouldReturnEmptyMapWhenNoQueryStringPresent() {
        // Given
        var fullOperation = usersOperationWithQueryParameters("limit");

        // When
        var queryParameters = resourceUriBuilder.extractQueryParameters(fullOperation, "api://users");

        // Then
        then(queryParameters).isEmpty();
    }

    @Test
    void shouldDropQueryParametersNotDeclaredOnOperation() {
        // Given
        var fullOperation = usersOperationWithQueryParameters("limit");

        // When
        var queryParameters =
                resourceUriBuilder.extractQueryParameters(fullOperation, "api://users?limit=10&injected=evil");

        // Then
        then(queryParameters).containsExactly(java.util.Map.entry("limit", "10"));
    }

    @Test
    void shouldInheritParametersDeclaredOnPathItem() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users/{id}": {
                  "parameters": [
                    { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } },
                    { "name": "expand", "in": "query", "schema": { "type": "string" } }
                  ],
                  "get": {
                    "operationId": "getUserById",
                    "parameters": [
                      { "name": "limit", "in": "query", "schema": { "type": "integer" } }
                    ]
                  }
                }
              }
            }
            """);

        // When & Then
        then(resourceUriBuilder.isTemplate(fullOperation)).isTrue();
        then(resourceUriBuilder.build(fullOperation)).isEqualTo("api://users/{id}{?expand,limit}");
        then(resourceUriBuilder.matchPathVariables(fullOperation, "api://users/123"))
                .containsExactly(java.util.Map.entry("id", "123"));
    }

    @Test
    void shouldLetOperationParameterOverrideInheritedPathItemParameter() {
        // Given
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "parameters": [
                    { "name": "limit", "in": "query", "description": "inherited", "schema": { "type": "string" } }
                  ],
                  "get": {
                    "operationId": "listUsers",
                    "parameters": [
                      { "name": "limit", "in": "query", "description": "overridden", "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);

        // When
        var queryParameters = resourceUriBuilder.queryParameters(fullOperation);

        // Then
        then(queryParameters).hasSize(1);
        then(queryParameters.getFirst().getDescription()).isEqualTo("overridden");
    }

    private FullOperation usersOperationWithQueryParameters(String... names) {
        var parameters = java.util.Arrays.stream(names)
                .map(name ->
                        "{ \"name\": \"%s\", \"in\": \"query\", \"schema\": { \"type\": \"string\" } }".formatted(name))
                .collect(java.util.stream.Collectors.joining(","));
        return fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "listUsers",
                    "parameters": [%s]
                  }
                }
              }
            }
            """.formatted(parameters));
    }

    private FullOperation fullOperation(String jsonSpec) {
        var openApi = parseOpenAPI(jsonSpec);
        var pathEntry = openApi.getPaths().entrySet().iterator().next();
        var operationEntry =
                pathEntry.getValue().readOperationsMap().entrySet().iterator().next();
        return new FullOperation(pathEntry.getKey(), operationEntry.getKey(), operationEntry.getValue(), openApi);
    }

    private OpenAPI parseOpenAPI(String jsonSpec) {
        return parser.readContents(jsonSpec).getOpenAPI();
    }
}
