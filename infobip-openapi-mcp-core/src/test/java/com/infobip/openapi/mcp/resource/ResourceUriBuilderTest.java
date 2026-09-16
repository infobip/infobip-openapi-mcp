package com.infobip.openapi.mcp.resource;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

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
        // When
        var queryParameters = resourceUriBuilder.extractQueryParameters("api://users?limit=10&expand=profile");

        // Then
        then(queryParameters)
                .containsExactly(java.util.Map.entry("limit", "10"), java.util.Map.entry("expand", "profile"));
    }

    @Test
    void shouldDecodeUrlEncodedQueryParameterValues() {
        // When
        var queryParameters = resourceUriBuilder.extractQueryParameters("api://users?name=John%20Doe");

        // Then
        then(queryParameters).containsExactly(java.util.Map.entry("name", "John Doe"));
    }

    @Test
    void shouldReturnEmptyMapWhenNoQueryStringPresent() {
        // When
        var queryParameters = resourceUriBuilder.extractQueryParameters("api://users");

        // Then
        then(queryParameters).isEmpty();
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
