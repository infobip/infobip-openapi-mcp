package com.infobip.openapi.mcp.resource;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

class ResourceExecutionExceptionTest {

    @Test
    void shouldUseResourceNotFoundErrorCodeForNotFound() {
        // When
        var exception = ResourceExecutionException.becauseNotFound("res://greet/Alice");

        // Then
        then(exception.getJsonRpcError().code()).isEqualTo(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND);
        then(exception.getJsonRpcError().message()).contains("res://greet/Alice");
    }

    @Test
    void shouldUseInternalErrorCodeForBackendCallFailed() {
        // When
        var exception = ResourceExecutionException.becauseBackendCallFailed(
                "res://greet", "/resources/greet", new RuntimeException("timeout"));

        // Then
        then(exception.getJsonRpcError().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
        then(exception.getJsonRpcError().message())
                .contains("res://greet")
                .contains("/resources/greet")
                .contains("timeout");
    }

    @Test
    void shouldPreserveCauseForBackendCallFailed() {
        // Given
        var cause = new RuntimeException("connection refused");

        // When
        var exception = ResourceExecutionException.becauseBackendCallFailed("res://greet", "/resources/greet", cause);

        // Then
        then(exception).hasCause(cause);
    }

    @Test
    void shouldNotHaveCauseForNotFound() {
        // When
        var exception = ResourceExecutionException.becauseNotFound("res://greet");

        // Then
        then(exception).hasNoCause();
    }
}
