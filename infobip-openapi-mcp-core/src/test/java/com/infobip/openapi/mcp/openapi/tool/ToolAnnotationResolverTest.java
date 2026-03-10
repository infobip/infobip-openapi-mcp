package com.infobip.openapi.mcp.openapi.tool;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToolAnnotationResolverTest {

    @ParameterizedTest
    @MethodSource("httpMethodDefaults")
    void shouldReturnCorrectDefaultsForHttpMethod(
            PathItem.HttpMethod givenMethod,
            boolean expectedReadOnly,
            boolean expectedDestructive,
            boolean expectedIdempotent) {
        // Given
        var givenResolver = new ToolAnnotationResolver(Map.of());
        var givenFullOperation = givenFullOperation(givenMethod, new Operation());

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(
                null, expectedReadOnly, expectedDestructive, expectedIdempotent, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    static Stream<Arguments> httpMethodDefaults() {
        return Stream.of(
                Arguments.of(PathItem.HttpMethod.GET, true, false, true),
                Arguments.of(PathItem.HttpMethod.HEAD, true, false, true),
                Arguments.of(PathItem.HttpMethod.OPTIONS, true, false, true),
                Arguments.of(PathItem.HttpMethod.PUT, false, false, true),
                Arguments.of(PathItem.HttpMethod.POST, false, false, false),
                Arguments.of(PathItem.HttpMethod.DELETE, false, true, true),
                Arguments.of(PathItem.HttpMethod.PATCH, false, false, false),
                Arguments.of(PathItem.HttpMethod.TRACE, true, false, true));
    }

    @Test
    void shouldOverrideWithVendorExtension() {
        // Given
        var givenResolver = new ToolAnnotationResolver(Map.of());
        var givenOperation = new Operation();
        givenOperation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("idempotentHint", true));
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.POST, givenOperation);

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldOverrideReturnDirectWithVendorExtension() {
        // Given
        var givenResolver = new ToolAnnotationResolver(Map.of());
        var givenOperation = new Operation();
        givenOperation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("returnDirect", true));
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.GET, givenOperation);

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, true, false, true, true, true);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIgnoreUnknownKeysInVendorExtension() {
        // Given
        var givenResolver = new ToolAnnotationResolver(Map.of());
        var givenOperation = new Operation();
        var givenExtensionMap = new LinkedHashMap<String, Object>();
        givenExtensionMap.put("unknownKey", "value");
        givenExtensionMap.put("readOnlyHint", false);
        givenOperation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, givenExtensionMap);
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.GET, givenOperation);

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIgnoreNonMapVendorExtension() {
        // Given
        var givenResolver = new ToolAnnotationResolver(Map.of());
        var givenOperation = new Operation();
        givenOperation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, "not-a-map");
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.GET, givenOperation);

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, true, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldOverrideWithConfigProperties() {
        // Given
        var givenConfigOverrides =
                Map.of("testTool", new OpenApiMcpProperties.Tools.Annotations(null, false, null, null, null));
        var givenResolver = new ToolAnnotationResolver(givenConfigOverrides);
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.DELETE, new Operation());

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldApplyConfigOverrideOverVendorExtension() {
        // Given
        var givenConfigOverrides =
                Map.of("testTool", new OpenApiMcpProperties.Tools.Annotations(null, null, false, null, null));
        var givenResolver = new ToolAnnotationResolver(givenConfigOverrides);
        var givenOperation = new Operation();
        givenOperation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("idempotentHint", true));
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.POST, givenOperation);

        // When
        var result = givenResolver.resolve(givenFullOperation, "testTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, false, false, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldComposeAllThreeLayers() {
        // Given
        var givenConfigOverrides =
                Map.of("myTool", new OpenApiMcpProperties.Tools.Annotations(null, null, null, false, null));
        var givenResolver = new ToolAnnotationResolver(givenConfigOverrides);
        var givenOperation = new Operation();
        givenOperation.addExtension(
                Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("destructiveHint", true, "returnDirect", true));
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.POST, givenOperation);

        // When
        var result = givenResolver.resolve(givenFullOperation, "myTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, true, false, false, true);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnPureDefaultsWhenNoExtensionsOrConfig() {
        // Given
        var givenResolver = new ToolAnnotationResolver(Map.of());
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.PUT, new Operation());

        // When
        var result = givenResolver.resolve(givenFullOperation, "unknownTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldNotOverrideWhenConfigToolNameDoesNotMatch() {
        // Given
        var givenConfigOverrides =
                Map.of("otherTool", new OpenApiMcpProperties.Tools.Annotations(true, true, true, false, true));
        var givenResolver = new ToolAnnotationResolver(givenConfigOverrides);
        var givenFullOperation = givenFullOperation(PathItem.HttpMethod.GET, new Operation());

        // When
        var result = givenResolver.resolve(givenFullOperation, "myTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, true, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private static FullOperation givenFullOperation(PathItem.HttpMethod method, Operation operation) {
        return new FullOperation("/test", method, operation, new OpenAPI());
    }
}
