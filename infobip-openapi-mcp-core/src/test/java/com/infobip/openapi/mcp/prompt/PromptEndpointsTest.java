package com.infobip.openapi.mcp.prompt;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PromptEndpointsTest {

    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();

    @Test
    void shouldReturnEmptySetWhenNoExtension() {
        // Given
        var givenOpenApi = openApiWithPrompts(null);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldCollectResolvePathOfResolvedPrompt() {
        // Given
        var givenOpenApi = openApiWithPrompts("""
                [{"name": "summarize", "resolve": {"path": "/prompts/x"}}]
                """);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).containsExactly("/prompts/x");
    }

    @Test
    void shouldCollectResolvePathsOfAllResolvedPrompts() {
        // Given
        var givenOpenApi = openApiWithPrompts("""
                [
                  {"name": "first", "resolve": {"path": "/prompts/a"}},
                  {"name": "second", "resolve": {"path": "/prompts/b"}}
                ]
                """);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).containsExactlyInAnyOrder("/prompts/a", "/prompts/b");
    }

    @Test
    void shouldNotCollectAnythingForInlinePrompt() {
        // Given
        var givenOpenApi = openApiWithPrompts("""
                [{"name": "greet", "messages": [{"role": "user", "content": "Hello {{name}}"}]}]
                """);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("absoluteResolvePaths")
    void shouldNotCollectAbsoluteResolvePath(String givenPath) {
        // Given
        var givenOpenApi = openApiWithPrompts("""
                [{"name": "summarize", "resolve": {"path": "%s"}}]
                """.formatted(givenPath));

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).isEmpty();
    }

    static Set<String> absoluteResolvePaths() {
        return Set.of("https://other.example.com/prompts/x", "http://other.example.com/prompts/x");
    }

    @ParameterizedTest
    @MethodSource("malformedExtensions")
    void shouldSkipMalformedEntriesWithoutThrowing(String givenExtension) {
        // Given
        var givenOpenApi = openApiWithPrompts(givenExtension);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).isEmpty();
    }

    static Set<String> malformedExtensions() {
        return Set.of(
                "\"not-a-list\"",
                "{\"name\": \"summarize\", \"resolve\": {\"path\": \"/prompts/x\"}}",
                "[\"not-a-map\"]",
                "[null]",
                "[{\"name\": \"summarize\", \"resolve\": \"/prompts/x\"}]",
                "[{\"name\": \"summarize\", \"resolve\": {}}]",
                "[{\"name\": \"summarize\", \"resolve\": {\"path\": \"\"}}]",
                "[{\"name\": \"summarize\", \"resolve\": {\"path\": \"   \"}}]",
                "[{\"name\": \"summarize\", \"resolve\": {\"path\": 42}}]",
                "[{\"name\": \"summarize\", \"resolve\": {\"path\": null}}]");
    }

    @Test
    void shouldCollectValidEntriesNextToMalformedOnes() {
        // Given
        var givenOpenApi = openApiWithPrompts("""
                [
                  "not-a-map",
                  {"name": "broken", "resolve": {"path": 42}},
                  {"name": "valid", "resolve": {"path": "/prompts/x"}}
                ]
                """);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        then(result).containsExactly("/prompts/x");
    }

    @Test
    void shouldReturnImmutableSet() {
        // Given
        var givenOpenApi = openApiWithPrompts("""
                [{"name": "summarize", "resolve": {"path": "/prompts/x"}}]
                """);

        // When
        var result = PromptEndpoints.relativeResolvePaths(givenOpenApi);

        // Then
        thenThrownBy(() -> result.add("/other")).isInstanceOf(UnsupportedOperationException.class);
    }

    private OpenAPI openApiWithPrompts(String promptsJson) {
        var extension = promptsJson == null ? "" : ", \"x-mcp-prompts\": " + promptsJson;
        return parser.readContents("""
                {
                  "openapi": "3.1.0",
                  "info": {"title": "Test API", "version": "1.0.0"},
                  "paths": {}%s
                }
                """.formatted(extension)).getOpenAPI();
    }
}
