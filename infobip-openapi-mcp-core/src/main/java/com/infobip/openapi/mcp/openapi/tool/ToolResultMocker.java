package com.infobip.openapi.mcp.openapi.tool;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

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
import java.util.Objects;
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
        // Runs before RegisteredTool, so that it can prevent
        // actual HTTP API calls from being made.
        return LOWEST_PRECEDENCE - 1;
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
                .map(this::extractJsonMediaType)
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

    private @Nullable MediaType extractJsonMediaType(@Nullable Content content) {
        if (content == null) {
            return null;
        }

        return content.keySet().stream()
                .filter(type -> type.contains(APPLICATION_JSON_VALUE))
                .findFirst()
                .map(content::get)
                .orElse(null);
    }

    private @Nullable Object extractExampleValue(@Nullable MediaType mediaType) {
        if (mediaType == null) {
            return null;
        }

        var example = mediaType.getExample();
        if (example != null) {
            return example;
        }

        var examples = mediaType.getExamples();
        if (examples == null || examples.isEmpty()) {
            return null;
        }

        return examples.values().stream()
                .map(Example::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private @Nullable String serializeToString(@Nullable Object exampleValue) {
        if (exampleValue == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(exampleValue);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
