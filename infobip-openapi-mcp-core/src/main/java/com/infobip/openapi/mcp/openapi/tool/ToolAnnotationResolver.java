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
                PathItem.HttpMethod.GET,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.HEAD,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.OPTIONS,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.TRACE,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.PUT,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(false)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.POST,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(false)
                        .destructiveHint(false)
                        .idempotentHint(false)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.DELETE,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(false)
                        .destructiveHint(true)
                        .idempotentHint(true)
                        .build());
        HTTP_METHOD_DEFAULTS.put(
                PathItem.HttpMethod.PATCH,
                ToolAnnotationsBuilder.builder()
                        .readOnlyHint(false)
                        .destructiveHint(false)
                        .idempotentHint(false)
                        .build());
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
        if (!HTTP_METHOD_DEFAULTS.containsKey(method)) {
            return ToolAnnotationsBuilder.defaultAnnotations();
        }
        return HTTP_METHOD_DEFAULTS.get(method);
    }

    private McpSchema.ToolAnnotations mergeVendorExtension(
            McpSchema.ToolAnnotations base, FullOperation fullOperation) {
        var extensions = fullOperation.operation().getExtensions();
        if (extensions == null) {
            return base;
        }

        if (extensions.get(Spec.MCP_ANNOTATIONS_EXTENSION) instanceof Map<?, ?> map) {
            return merge(
                    base,
                    toBooleanOrNull(map.get("readOnlyHint")),
                    toBooleanOrNull(map.get("destructiveHint")),
                    toBooleanOrNull(map.get("idempotentHint")),
                    toBooleanOrNull(map.get("openWorldHint")),
                    toBooleanOrNull(map.get("returnDirect")));
        }
        return base;
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
        if (isAllNull(readOnlyHint, destructiveHint, idempotentHint, openWorldHint, returnDirect)) {
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
    private Boolean toBooleanOrNull(@Nullable Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return null;
    }

    private boolean isAllNull(Boolean... annotations) {
        for (Boolean annotation : annotations) {
            if (annotation != null) {
                return false;
            }
        }
        return true;
    }

    private static class ToolAnnotationsBuilder {
        private String title = null;
        private Boolean readOnlyHint = null;
        private Boolean destructiveHint = null;
        private Boolean idempotentHint = null;
        private Boolean openWorldHint = Boolean.TRUE;

        public static ToolAnnotationsBuilder builder() {
            return new ToolAnnotationsBuilder();
        }

        public static McpSchema.ToolAnnotations defaultAnnotations() {
            return new McpSchema.ToolAnnotations(null, false, false, false, true, null);
        }

        public ToolAnnotationsBuilder title(String title) {
            this.title = title;
            return this;
        }

        public ToolAnnotationsBuilder readOnlyHint(Boolean readOnlyHint) {
            this.readOnlyHint = readOnlyHint;
            return this;
        }

        public ToolAnnotationsBuilder destructiveHint(Boolean destructiveHint) {
            this.destructiveHint = destructiveHint;
            return this;
        }

        public ToolAnnotationsBuilder idempotentHint(Boolean idempotentHint) {
            this.idempotentHint = idempotentHint;
            return this;
        }

        public ToolAnnotationsBuilder openWorldHint(Boolean openWorldHint) {
            this.openWorldHint = openWorldHint;
            return this;
        }

        public McpSchema.ToolAnnotations build() {
            return new McpSchema.ToolAnnotations(
                    title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint, null);
        }
    }
}
