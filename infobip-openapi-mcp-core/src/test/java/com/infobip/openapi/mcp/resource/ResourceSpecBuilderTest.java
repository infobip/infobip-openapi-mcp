package com.infobip.openapi.mcp.resource;

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
class ResourceSpecBuilderTest {

    @Mock
    private McpRequestContextFactory contextFactory;

    @Test
    void shouldBuildSyncResourceSpecificationWithCorrectResource() {
        // Given
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (ctx, req) -> new McpSchema.ReadResourceResult(List.of(), null);
        var registeredResource = new RegisteredResource(
                McpSchema.Resource.builder("res://greet", "greet").build(), null, handler);
        var builder = new ResourceSpecBuilder(List.of(), contextFactory);

        // When
        var spec = builder.buildSyncResourceSpecification(registeredResource);

        // Then
        then(spec.resource()).isEqualTo(registeredResource.resource());
    }

    @Test
    void shouldBuildSyncStatelessResourceSpecificationWithCorrectResource() {
        // Given
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (ctx, req) -> new McpSchema.ReadResourceResult(List.of(), null);
        var registeredResource = new RegisteredResource(
                McpSchema.Resource.builder("res://greet", "greet").build(), null, handler);
        var builder = new ResourceSpecBuilder(List.of(), contextFactory);

        // When
        var spec = builder.buildSyncStatelessResourceSpecification(registeredResource);

        // Then
        then(spec.resource()).isEqualTo(registeredResource.resource());
    }

    @Test
    void shouldBuildSyncResourceTemplateSpecificationWithCorrectTemplate() {
        // Given
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (ctx, req) -> new McpSchema.ReadResourceResult(List.of(), null);
        var registeredResource = new RegisteredResource(
                null,
                McpSchema.ResourceTemplate.builder("res://greet/{name}", "greet")
                        .build(),
                handler);
        var builder = new ResourceSpecBuilder(List.of(), contextFactory);

        // When
        var spec = builder.buildSyncResourceTemplateSpecification(registeredResource);

        // Then
        then(spec.resourceTemplate()).isEqualTo(registeredResource.template());
    }

    @Test
    void shouldBuildSyncStatelessResourceTemplateSpecificationWithCorrectTemplate() {
        // Given
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (ctx, req) -> new McpSchema.ReadResourceResult(List.of(), null);
        var registeredResource = new RegisteredResource(
                null,
                McpSchema.ResourceTemplate.builder("res://greet/{name}", "greet")
                        .build(),
                handler);
        var builder = new ResourceSpecBuilder(List.of(), contextFactory);

        // When
        var spec = builder.buildSyncStatelessResourceTemplateSpecification(registeredResource);

        // Then
        then(spec.resourceTemplate()).isEqualTo(registeredResource.template());
    }

    @Test
    void shouldInvokeHandlerThroughFilterChain() {
        // Given
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (ctx, req) -> new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(req.uri(), "text/plain", "content", null)), null);
        var registeredResource = new RegisteredResource(
                McpSchema.Resource.builder("res://greet", "greet").build(), null, handler);
        var builder = new ResourceSpecBuilder(List.of(), contextFactory);
        var spec = builder.buildSyncStatelessResourceSpecification(registeredResource);
        var request = new McpSchema.ReadResourceRequest("res://greet", null);

        // When
        var result = spec.readHandler().apply(null, request);

        // Then
        then(result.contents()).hasSize(1);
        then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                .isEqualTo("content");
    }
}
