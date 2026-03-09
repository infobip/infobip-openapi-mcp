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
            PathItem.HttpMethod method,
            boolean expectedReadOnly,
            boolean expectedDestructive,
            boolean expectedIdempotent) {
        // Given
        var resolver = new ToolAnnotationResolver(Map.of());
        var fullOperation = givenFullOperation(method, new Operation());

        // When
        var result = resolver.resolve(fullOperation, "testTool");

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
        var resolver = new ToolAnnotationResolver(Map.of());
        var operation = new Operation();
        operation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("idempotentHint", true));
        var fullOperation = givenFullOperation(PathItem.HttpMethod.POST, operation);

        // When
        var result = resolver.resolve(fullOperation, "testTool");

        // Then — POST defaults with idempotentHint overridden to true
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldOverrideReturnDirectWithVendorExtension() {
        // Given
        var resolver = new ToolAnnotationResolver(Map.of());
        var operation = new Operation();
        operation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("returnDirect", true));
        var fullOperation = givenFullOperation(PathItem.HttpMethod.GET, operation);

        // When
        var result = resolver.resolve(fullOperation, "testTool");

        // Then — GET defaults with returnDirect overridden to true
        var expected = new McpSchema.ToolAnnotations(null, true, false, true, true, true);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIgnoreUnknownKeysInVendorExtension() {
        // Given
        var resolver = new ToolAnnotationResolver(Map.of());
        var operation = new Operation();
        var extensionMap = new LinkedHashMap<String, Object>();
        extensionMap.put("unknownKey", "value");
        extensionMap.put("readOnlyHint", false);
        operation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, extensionMap);
        var fullOperation = givenFullOperation(PathItem.HttpMethod.GET, operation);

        // When
        var result = resolver.resolve(fullOperation, "testTool");

        // Then — GET defaults with readOnlyHint overridden to false, unknown key ignored
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIgnoreNonMapVendorExtension() {
        // Given
        var resolver = new ToolAnnotationResolver(Map.of());
        var operation = new Operation();
        operation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, "not-a-map");
        var fullOperation = givenFullOperation(PathItem.HttpMethod.GET, operation);

        // When
        var result = resolver.resolve(fullOperation, "testTool");

        // Then — pure GET defaults (vendor extension ignored)
        var expected = new McpSchema.ToolAnnotations(null, true, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldOverrideWithConfigProperties() {
        // Given
        var configOverrides =
                Map.of("testTool", new OpenApiMcpProperties.Tools.Annotations(null, true, null, null, null));
        var resolver = new ToolAnnotationResolver(configOverrides);
        var fullOperation = givenFullOperation(PathItem.HttpMethod.DELETE, new Operation());

        // When
        var result = resolver.resolve(fullOperation, "testTool");

        // Then — DELETE defaults with destructiveHint overridden to true (from config)
        // but wait — DELETE already has destructiveHint=true, so let's override it to false
        var configOverrides2 =
                Map.of("testTool", new OpenApiMcpProperties.Tools.Annotations(null, false, null, null, null));
        var resolver2 = new ToolAnnotationResolver(configOverrides2);

        // When
        var result2 = resolver2.resolve(fullOperation, "testTool");

        // Then — DELETE defaults with destructiveHint overridden to false
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result2).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldApplyConfigOverrideOverVendorExtension() {
        // Given — vendor extension sets idempotentHint=true, config sets it back to false
        var configOverrides =
                Map.of("testTool", new OpenApiMcpProperties.Tools.Annotations(null, null, false, null, null));
        var resolver = new ToolAnnotationResolver(configOverrides);
        var operation = new Operation();
        operation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("idempotentHint", true));
        var fullOperation = givenFullOperation(PathItem.HttpMethod.POST, operation);

        // When
        var result = resolver.resolve(fullOperation, "testTool");

        // Then — POST defaults, vendor extension sets idempotentHint=true, config overrides to false
        var expected = new McpSchema.ToolAnnotations(null, false, false, false, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldComposeAllThreeLayers() {
        // Given
        // HTTP default for POST: readOnly=false, destructive=false, idempotent=false, openWorld=true
        // Vendor extension: destructiveHint=true, returnDirect=true
        // Config: openWorldHint=false
        var configOverrides =
                Map.of("myTool", new OpenApiMcpProperties.Tools.Annotations(null, null, null, false, null));
        var resolver = new ToolAnnotationResolver(configOverrides);
        var operation = new Operation();
        operation.addExtension(Spec.MCP_ANNOTATIONS_EXTENSION, Map.of("destructiveHint", true, "returnDirect", true));
        var fullOperation = givenFullOperation(PathItem.HttpMethod.POST, operation);

        // When
        var result = resolver.resolve(fullOperation, "myTool");

        // Then
        var expected = new McpSchema.ToolAnnotations(null, false, true, false, false, true);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnPureDefaultsWhenNoExtensionsOrConfig() {
        // Given
        var resolver = new ToolAnnotationResolver(Map.of());
        var fullOperation = givenFullOperation(PathItem.HttpMethod.PUT, new Operation());

        // When
        var result = resolver.resolve(fullOperation, "unknownTool");

        // Then — pure PUT defaults
        var expected = new McpSchema.ToolAnnotations(null, false, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldNotOverrideWhenConfigToolNameDoesNotMatch() {
        // Given
        var configOverrides =
                Map.of("otherTool", new OpenApiMcpProperties.Tools.Annotations(true, true, true, false, true));
        var resolver = new ToolAnnotationResolver(configOverrides);
        var fullOperation = givenFullOperation(PathItem.HttpMethod.GET, new Operation());

        // When
        var result = resolver.resolve(fullOperation, "myTool");

        // Then — pure GET defaults, config for "otherTool" not applied
        var expected = new McpSchema.ToolAnnotations(null, true, false, true, true, null);
        then(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private static FullOperation givenFullOperation(PathItem.HttpMethod method, Operation operation) {
        return new FullOperation("/test", method, operation, new OpenAPI());
    }
}
