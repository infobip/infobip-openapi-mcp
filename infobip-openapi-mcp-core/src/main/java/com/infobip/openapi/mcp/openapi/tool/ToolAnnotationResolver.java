package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.PathItem;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Resolves MCP tool annotations from a combination of HTTP method defaults,
 * {@code x-mcp-annotations} vendor extension on the OpenAPI operation, and
 * YAML configuration properties per tool name.
 * <p>
 * Override precedence (lowest to highest):
 * <ol>
 *   <li>HTTP method defaults (always applied)</li>
 *   <li>{@code x-mcp-annotations} vendor extension on the OpenAPI Operation</li>
 *   <li>YAML configuration properties per tool name</li>
 * </ol>
 * Each layer only overrides fields it explicitly sets; unset fields fall through from the previous layer.
 */
public class ToolAnnotationResolver {

    private static final EnumMap<PathItem.HttpMethod, McpSchema.ToolAnnotations> HTTP_METHOD_DEFAULTS =
            new EnumMap<>(PathItem.HttpMethod.class);

    static {
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.GET, new McpSchema.ToolAnnotations(null, true, false, true, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.HEAD, new McpSchema.ToolAnnotations(null, true, false, true, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.OPTIONS, new McpSchema.ToolAnnotations(null, true, false, true, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.PUT, new McpSchema.ToolAnnotations(null, false, false, true, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.POST, new McpSchema.ToolAnnotations(null, false, false, false, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.DELETE, new McpSchema.ToolAnnotations(null, false, true, true, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.PATCH, new McpSchema.ToolAnnotations(null, false, false, false, true, null));
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.TRACE, new McpSchema.ToolAnnotations(null, true, false, true, true, null));
    }

    private final Map<String, OpenApiMcpProperties.Tools.Annotations> configOverrides;

    public ToolAnnotationResolver(Map<String, OpenApiMcpProperties.Tools.Annotations> configOverrides) {
        this.configOverrides = configOverrides;
    }

    /**
     * Resolves the final MCP tool annotations for the given operation and tool name.
     *
     * @param fullOperation the OpenAPI operation
     * @param toolName      the resolved tool name
     * @return the composed tool annotations
     */
    public McpSchema.ToolAnnotations resolve(FullOperation fullOperation, String toolName) {
        var result = fromHttpMethod(fullOperation.method());
        result = mergeVendorExtension(result, fullOperation);
        result = mergeConfigOverride(result, toolName);
        return result;
    }

    static McpSchema.ToolAnnotations fromHttpMethod(PathItem.HttpMethod method) {
        var defaults = HTTP_METHOD_DEFAULTS.get(method);
        if (defaults != null) {
            return defaults;
        }
        return new McpSchema.ToolAnnotations(null, false, false, false, true, null);
    }

    @SuppressWarnings("unchecked")
    private McpSchema.ToolAnnotations mergeVendorExtension(
            McpSchema.ToolAnnotations base, FullOperation fullOperation) {
        var extensions = fullOperation.operation().getExtensions();
        if (extensions == null) {
            return base;
        }
        var raw = extensions.get(Spec.MCP_ANNOTATIONS_EXTENSION);
        if (!(raw instanceof Map<?, ?>)) {
            return base;
        }
        var map = (Map<String, Object>) raw;
        return merge(
                base,
                toBooleanOrNull(map.get("readOnlyHint")),
                toBooleanOrNull(map.get("destructiveHint")),
                toBooleanOrNull(map.get("idempotentHint")),
                toBooleanOrNull(map.get("openWorldHint")),
                toBooleanOrNull(map.get("returnDirect")));
    }

    private McpSchema.ToolAnnotations mergeConfigOverride(McpSchema.ToolAnnotations base, String toolName) {
        var override = configOverrides.get(toolName);
        if (override == null) {
            return base;
        }
        return merge(
                base,
                override.readOnlyHint(),
                override.destructiveHint(),
                override.idempotentHint(),
                override.openWorldHint(),
                override.returnDirect());
    }

    private McpSchema.ToolAnnotations merge(
            McpSchema.ToolAnnotations base,
            @Nullable Boolean readOnlyHint,
            @Nullable Boolean destructiveHint,
            @Nullable Boolean idempotentHint,
            @Nullable Boolean openWorldHint,
            @Nullable Boolean returnDirect) {
        if (readOnlyHint == null
                && destructiveHint == null
                && idempotentHint == null
                && openWorldHint == null
                && returnDirect == null) {
            return base;
        }
        return new McpSchema.ToolAnnotations(
                base.title(),
                readOnlyHint != null ? readOnlyHint : base.readOnlyHint(),
                destructiveHint != null ? destructiveHint : base.destructiveHint(),
                idempotentHint != null ? idempotentHint : base.idempotentHint(),
                openWorldHint != null ? openWorldHint : base.openWorldHint(),
                returnDirect != null ? returnDirect : base.returnDirect());
    }

    @Nullable
    private static Boolean toBooleanOrNull(@Nullable Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return null;
    }
}
