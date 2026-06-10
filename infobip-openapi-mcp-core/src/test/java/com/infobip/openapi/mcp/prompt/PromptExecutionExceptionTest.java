package com.infobip.openapi.mcp.prompt;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptExecutionExceptionTest {

    @Test
    void shouldUseInvalidParamsErrorCodeForMissingRequiredArguments() {
        // When
        var exception = PromptExecutionException.becauseMissingRequiredArguments("greet", List.of("name", "age"));

        // Then
        then(exception.getJsonRpcError().code()).isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS);
        then(exception.getJsonRpcError().message()).contains("greet").contains("[name, age]");
    }

    @Test
    void shouldUseInternalErrorCodeForBackendCallFailed() {
        // When
        var exception = PromptExecutionException.becauseBackendCallFailed(
                "greet", "/prompts/greet", new RuntimeException("timeout"));

        // Then
        then(exception.getJsonRpcError().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
        then(exception.getJsonRpcError().message())
                .contains("greet")
                .contains("/prompts/greet")
                .contains("timeout");
    }

    @Test
    void shouldUseInternalErrorCodeForBackendResponseInvalid() {
        // When
        var exception = PromptExecutionException.becauseBackendResponseInvalid(
                "greet", new RuntimeException("Unrecognized token 'foo'"));

        // Then
        then(exception.getJsonRpcError().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
        then(exception.getJsonRpcError().message()).contains("greet");
    }

    @Test
    void shouldPreserveCauseForBackendCallFailed() {
        // Given
        var cause = new RuntimeException("connection refused");

        // When
        var exception = PromptExecutionException.becauseBackendCallFailed("greet", "/prompts/greet", cause);

        // Then
        then(exception).hasCause(cause);
    }

    @Test
    void shouldPreserveCauseForBackendResponseInvalid() {
        // Given
        var cause = new RuntimeException("parse error");

        // When
        var exception = PromptExecutionException.becauseBackendResponseInvalid("greet", cause);

        // Then
        then(exception).hasCause(cause);
    }

    @Test
    void shouldNotHaveCauseForMissingRequiredArguments() {
        // When
        var exception = PromptExecutionException.becauseMissingRequiredArguments("greet", List.of("name"));

        // Then
        then(exception).hasNoCause();
    }
}
