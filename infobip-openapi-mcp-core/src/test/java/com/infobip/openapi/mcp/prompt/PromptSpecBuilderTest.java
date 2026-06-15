package com.infobip.openapi.mcp.prompt;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptSpecBuilderTest {

    @Mock
    private McpRequestContextFactory contextFactory;

    @Test
    void shouldBuildSyncPromptSpecificationWithCorrectPrompt() {
        // Given
        BiFunction<McpRequestContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> handler =
                (ctx, req) -> new McpSchema.GetPromptResult("test", List.of());
        var registeredPrompt =
                new RegisteredPrompt(new McpSchema.Prompt("greet", "A greeting prompt", List.of()), handler);
        var builder = new PromptSpecBuilder(List.of(), contextFactory);

        // When
        var spec = builder.buildSyncPromptSpecification(registeredPrompt);

        // Then
        then(spec.prompt()).isEqualTo(registeredPrompt.prompt());
    }

    @Test
    void shouldBuildSyncStatelessPromptSpecificationWithCorrectPrompt() {
        // Given
        BiFunction<McpRequestContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> handler =
                (ctx, req) -> new McpSchema.GetPromptResult("test", List.of());
        var registeredPrompt =
                new RegisteredPrompt(new McpSchema.Prompt("greet", "A greeting prompt", List.of()), handler);
        var builder = new PromptSpecBuilder(List.of(), contextFactory);

        // When
        var spec = builder.buildSyncStatelessPromptSpecification(registeredPrompt);

        // Then
        then(spec.prompt()).isEqualTo(registeredPrompt.prompt());
    }

    @Test
    void shouldIncludeCorrectPromptNameInSpec() {
        // Given
        BiFunction<McpRequestContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> handler =
                (ctx, req) -> new McpSchema.GetPromptResult("result", List.of());
        var registeredPrompt =
                new RegisteredPrompt(new McpSchema.Prompt("custom-prompt", "Custom", List.of()), handler);
        var builder = new PromptSpecBuilder(List.of(), contextFactory);

        // When
        var syncSpec = builder.buildSyncPromptSpecification(registeredPrompt);
        var statelessSpec = builder.buildSyncStatelessPromptSpecification(registeredPrompt);

        // Then
        then(syncSpec.prompt().name()).isEqualTo("custom-prompt");
        then(statelessSpec.prompt().name()).isEqualTo("custom-prompt");
    }
}
