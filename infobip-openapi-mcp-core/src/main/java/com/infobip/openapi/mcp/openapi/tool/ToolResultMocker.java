package com.infobip.openapi.mcp.openapi.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

@NullMarked
public class ToolResultMocker implements ToolCallFilter, Ordered {

    /**
     * Runs before RegisteredTool, so that it can prevent
     * actual HTTP API calls from being made.
     */
    public static final Integer ORDER = RegisteredTool.ORDER - 1;

    public static final String SUPPORTED_MEDIA_TYPE = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolResultMocker.class);
    private static final String MISSING_EXAMPLE_ERR_MSG = "Missing OpenAPI example for mocking";

    private final ObjectMapper objectMapper;
    private final OpenApiMcpProperties properties;

    public ToolResultMocker(ObjectMapper objectMapper, OpenApiMcpProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ToolResultMocker.ORDER;
    }

    @Override
    public McpSchema.CallToolResult doFilter(
            McpRequestContext ctx, McpSchema.CallToolRequest req, ToolCallFilterChain chain) {
        if (!properties.tools().mock()) {
            return chain.doFilter(ctx, req);
        }

        LOGGER.trace("Mocking tool call {}...", req.name());

        var fullOperation = ctx.openApiOperation();
        if (fullOperation == null) {
            LOGGER.error("Missing OpenAPI operation for tool {}, returning MCP error response", req.name());
            return new McpSchema.CallToolResult(MISSING_EXAMPLE_ERR_MSG, true);
        }

        var responseBody = pickExample(fullOperation.operation());
        if (responseBody == null) {
            LOGGER.error(
                    "Missing examples in OpenAPI operation {}: {} for tool {}, returning MCP error response",
                    fullOperation.method(),
                    fullOperation.path(),
                    req.name());
            return new McpSchema.CallToolResult(MISSING_EXAMPLE_ERR_MSG, true);
        }

        LOGGER.trace("Returning mock to tool call {}:\n{}", req.name(), responseBody);
        return new McpSchema.CallToolResult(responseBody, false);
    }

    @Nullable
    private String pickExample(Operation operation) {
        var responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return null;
        }

        return successfulResponseExtractors()
                .map(extractor -> extractor.apply(responses))
                .filter(Objects::nonNull)
                .map(ApiResponse::getContent)
                .map(this::extractSupportedMediaType)
                .map(this::extractExampleValue)
                .map(this::serializeToString)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Stream<Function<ApiResponses, @Nullable ApiResponse>> successfulResponseExtractors() {
        return Stream.concat(
                Stream.of(res -> res.getDefault(), res -> res.get("2xx")),
                IntStream.range(200, 300).mapToObj(Integer::toString).map(status -> res -> res.get(status)));
    }

    private @Nullable MediaType extractSupportedMediaType(@Nullable Content content) {
        if (content == null) {
            return null;
        }

        return content.keySet().stream()
                .filter(type -> type.contains(SUPPORTED_MEDIA_TYPE))
                .findFirst()
                .map(content::get)
                .orElse(null);
    }

    private @Nullable Object extractExampleValue(@Nullable MediaType mediaType) {
        return Optional.ofNullable(mediaType).map(MediaType::getExamples).map(Map::values).stream()
                .flatMap(Collection::stream)
                .map(Example::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .or(() -> Optional.ofNullable(mediaType).map(MediaType::getExample))
                .orElse(null);
    }

    private @Nullable String serializeToString(@Nullable Object exampleValue) {
        if (exampleValue == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(exampleValue);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to serialize example into JSON. Example value: `{}`", exampleValue, e);
            return null;
        }
    }
}
