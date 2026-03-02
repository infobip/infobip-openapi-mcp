package com.infobip.openapi.mcp.openapi.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.ComposedExample;
import com.infobip.openapi.mcp.openapi.schema.InputExampleComposer;
import com.infobip.openapi.mcp.openapi.schema.InputSchemaComposer;
import com.infobip.openapi.mcp.openapi.tool.exception.ToolRegistrationException;
import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategy;
import com.infobip.openapi.mcp.util.OpenApiMapperFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for converting OpenAPI operations into MCP (Model Context Protocol) tool specifications.
 * <p>
 * This component is responsible for:
 * <ul>
 *   <li>Reading OpenAPI specifications from the {@link OpenApiRegistry}</li>
 *   <li>Converting each OpenAPI operation into an MCP tool specification</li>
 *   <li>Generating tool names using configurable {@link NamingStrategy}</li>
 *   <li>Resolving JSON schemas for tool input parameters</li>
 *   <li>Creating executable tool handlers for API calls</li>
 * </ul>
 * <p>
 * The registry processes all paths and operations defined in the OpenAPI specification
 * and creates corresponding MCP tools that can be executed by MCP clients. Each tool
 * represents a single API operation with its input schema and execution logic.
 *
 * @see OpenApiRegistry
 * @see NamingStrategy
 * @see ToolHandler
 * @see InputSchemaComposer
 */
public class ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);

    private final OpenApiRegistry openApiRegistry;
    private final NamingStrategy namingStrategy;
    private final InputSchemaComposer inputSchemaComposer;
    private final InputExampleComposer inputExampleComposer;
    private final ToolHandler toolHandler;
    private final OpenApiMapperFactory openApiMapperFactory;
    private final ObjectMapper jsonSchemaMapper = new ObjectMapper();
    private final ObjectMapper prettyPrintMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final OpenApiMcpProperties properties;

    private List<RegisteredTool> registeredToolsCache = List.of();

    public ToolRegistry(
            OpenApiRegistry openApiRegistry,
            NamingStrategy namingStrategy,
            InputSchemaComposer inputSchemaComposer,
            InputExampleComposer inputExampleComposer,
            ToolHandler toolHandler,
            OpenApiMapperFactory openApiMapperFactory,
            OpenApiMcpProperties properties) {
        this.openApiRegistry = openApiRegistry;
        this.namingStrategy = namingStrategy;
        this.inputSchemaComposer = inputSchemaComposer;
        this.inputExampleComposer = inputExampleComposer;
        this.toolHandler = toolHandler;
        this.openApiMapperFactory = openApiMapperFactory;
        this.properties = properties;
    }

    /**
     * Retrieves all available MCP tool specifications from the OpenAPI specification.
     * <p>
     * This method processes the OpenAPI specification and converts each operation
     * into an MCP tool specification. The process includes:
     * <ol>
     *   <li>Loading the OpenAPI specification from the registry</li>
     *   <li>Iterating through all paths and their operations</li>
     *   <li>Generating tool names using the configured naming strategy</li>
     *   <li>Resolving JSON schemas for input parameters</li>
     *   <li>Creating executable tool specifications</li>
     * </ol>
     *
     * @return a list of registered tools, one for each OpenAPI operation
     * @throws ToolRegistrationException if a tool name cannot be determined for any operation or JSON schema resolution fails critically
     */
    public List<RegisteredTool> getTools() {
        var openApi = openApiRegistry.openApi();
        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            return List.of();
        }
        var registeredTools = openApi.getPaths().entrySet().stream()
                .flatMap(pathEntry -> pathEntry.getValue().readOperationsMap().entrySet().stream()
                        .map(operationEntry -> new FullOperation(
                                pathEntry.getKey(), operationEntry.getKey(), operationEntry.getValue(), openApi)))
                .map(fullOperation -> {
                    var toolName = determineToolName(fullOperation);
                    var tool = McpSchema.Tool.builder()
                            .name(toolName)
                            .title(resolveTitle(fullOperation, toolName))
                            .description(buildDescription(fullOperation))
                            .inputSchema(resolveJsonSchema(fullOperation))
                            .build();

                    return new RegisteredTool(
                            tool,
                            (callToolRequest, context) -> {
                                var decomposedArguments =
                                        inputSchemaComposer.decompose(callToolRequest, fullOperation.operation());
                                return toolHandler.handleToolCall(fullOperation, decomposedArguments, context);
                            },
                            fullOperation);
                })
                .toList();
        this.registeredToolsCache = List.copyOf(registeredTools);
        return registeredTools;
    }

    private String determineToolName(FullOperation operation) {
        try {
            return namingStrategy.name(operation);
        } catch (RuntimeException exception) {
            throw ToolRegistrationException.becauseNameCannotBeDetermined(operation, exception);
        }
    }

    /**
     * Resolves the title for a tool with fallback logic.
     * <p>
     * If the operation has a non-blank summary, it will be used as the title.
     * Otherwise, the tool name will be used as a fallback.
     *
     * @param fullOperation the OpenAPI operation
     * @param toolName the name of the tool (used as fallback)
     * @return the resolved title
     */
    private String resolveTitle(FullOperation fullOperation, String toolName) {
        var summary = fullOperation.operation().getSummary();
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return toolName;
    }

    /**
     * Resolves the JSON schema for an OpenAPI operation's input parameters.
     * <p>
     * This method creates a JSON schema representation of the operation's parameters
     * that can be used by MCP clients to understand the expected input format.
     * The schema is generated based on the OpenAPI specification version.
     *
     * @param fullOperation the OpenAPI operation to create a schema for
     * @return a JSON string representing the input schema, or "{}" if resolution fails
     */
    private McpSchema.JsonSchema resolveJsonSchema(FullOperation fullOperation) {
        var composedSchema = inputSchemaComposer.compose(fullOperation);
        if (composedSchema == null) {
            // Due to the way how the underlying framework works, we need to explicitly provide an empty object schema
            return new McpSchema.JsonSchema("object", Map.of(), null, null, null, null);
        }

        try {
            var stringSchemaRepresentation =
                    openApiMapperFactory.mapper(fullOperation.openApi()).writeValueAsString(composedSchema);
            LOGGER.debug(
                    "Resolved JSON schema for operation {}: {}",
                    fullOperation.operation().getOperationId(),
                    stringSchemaRepresentation);
            return jsonSchemaMapper.readValue(stringSchemaRepresentation, McpSchema.JsonSchema.class);
        } catch (JsonProcessingException exception) {
            LOGGER.error(
                    "Failed to resolve JSON schema for operation: {}",
                    fullOperation.operation().getOperationId(),
                    exception);
            throw ToolRegistrationException.becauseOfJsonSchemaProcessingError(
                    fullOperation.operation().getOperationId(), exception);
        }
    }

    /**
     * Builds the description for a tool, optionally prepending the summary as a markdown title
     * and appending request examples as a JSON code block.
     * <p>
     * If the {@code prependSummaryToDescription} property is enabled, both summary and description exist,
     * the summary will be prepended to the description as a markdown H1 heading in the format:
     * {@code # {summary}\n\n{description}}
     * <p>
     * If request examples exist in the OpenAPI spec (as determined by the configured
     * {@code examplesMode}), they are appended as a Markdown JSON code block.
     * <p>
     * If only one of summary/description exists, returns whichever is present.
     *
     * @param fullOperation the OpenAPI operation
     * @return the constructed description, or null if neither description, summary, nor examples exist
     */
    private String buildDescription(FullOperation fullOperation) {
        var summary = fullOperation.operation().getSummary();
        var description = fullOperation.operation().getDescription();

        var hasSummary = summary != null && !summary.isBlank();
        var hasDescription = description != null && !description.isBlank();

        String baseDescription = null;

        // If both exist and feature is enabled, prepend summary as markdown H1 title
        if (hasSummary && hasDescription && properties.tools().prependSummaryToDescription()) {
            baseDescription = "# " + summary + "\n\n" + description;
        } else if (hasDescription) {
            baseDescription = description;
        } else if (hasSummary) {
            baseDescription = summary;
        }

        // Append examples if feature is enabled
        var exampleBlock = buildExampleBlock(fullOperation);
        if (exampleBlock != null) {
            return baseDescription != null ? baseDescription + "\n\n" + exampleBlock : exampleBlock;
        }

        return baseDescription;
    }

    /**
     * Builds a Markdown block from the composed examples for the given operation.
     * <p>
     * A single unnamed example is rendered as:
     * <pre>
     * ## Example
     *
     * ```json
     * { ... }
     * ```
     * </pre>
     * Multiple examples, or a single example that carries a title, are each rendered as:
     * <pre>
     * ### {title}
     *
     * {description}
     *
     * ```json
     * { ... }
     * ```
     * </pre>
     * where the description paragraph is omitted when absent.
     *
     * @param fullOperation the OpenAPI operation
     * @return a Markdown block string, or null if no examples found or serialization fails
     */
    private String buildExampleBlock(FullOperation fullOperation) {
        var examples = inputExampleComposer.composeExamples(fullOperation);
        if (examples.isEmpty()) {
            return null;
        }

        try {
            if (examples.size() == 1 && examples.get(0).title() == null) {
                var json = prettyPrintMapper.writeValueAsString(examples.get(0).value());
                return "## Example\n\n```json\n" + json + "\n```";
            }

            var sb = new StringBuilder("## Examples");
            for (ComposedExample composedExample : examples) {
                sb.append("\n\n");
                var title = composedExample.title();
                sb.append("### ").append(title != null ? title : "Example");
                if (composedExample.description() != null) {
                    sb.append("\n\n").append(composedExample.description());
                }
                var json = prettyPrintMapper.writeValueAsString(composedExample.value());
                sb.append("\n\n```json\n").append(json).append("\n```");
            }
            return sb.toString();
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "Failed to serialize example for operation '{}': {}",
                    fullOperation.operation().getOperationId(),
                    exception.getMessage());
            return null;
        }
    }

    public List<RegisteredTool> getRegisteredToolsCache() {
        return registeredToolsCache;
    }
}
