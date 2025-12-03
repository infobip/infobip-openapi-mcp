package com.infobip.openapi.mcp.openapi.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/**
 * Utility class for detecting and correcting JSON double serialization issues.
 *
 * <p>This is a temporary mitigation for cases where nested JSON constructs (objects and arrays)
 * are incorrectly wrapped as strings due to double serialization during MCP client integrations.
 * The long-term solution should implement full JSON schema validation at the MCP server level
 * to prevent these issues entirely.</p>
 *
 * <p>Example of double serialization issue:
 * <pre>
 * {
 *   "messages": "[{\"destinations\":[{\"to\":\"some destination\"}],\"content\":{\"text\":\"Hello\"}}]"
 * }
 * </pre>
 * Should be:
 * <pre>
 * {
 *   "messages": [{"destinations":[{"to":"some destination"}],"content":{"text":"Hello"}}]
 * }
 * </pre>
 * </p>
 */
@NullMarked
public class JsonDoubleSerializationCorrector {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonDoubleSerializationCorrector.class);

    private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Detects JSON double serialization and corrects it if found in request body.
     * Only processes bodies with JSON-compatible media types.
     *
     * @param body the request body to inspect and potentially correct
     * @return Optional containing the corrected body if double serialization was detected and corrected,
     *         or empty Optional if no correction was needed, not possible, or media type is not JSON-compatible
     */
    public Optional<DecomposedRequestData.Body> correctIfDetected(DecomposedRequestData.Body body) {
        if (!body.targetContentType().isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return Optional.empty();
        }
        return correctIfDetected(body.content())
                .map(correctedContent -> new DecomposedRequestData.Body(body.targetContentType(), correctedContent));
    }

    /**
     * Detects JSON double serialization and corrects it if found.
     *
     * @param payload the JSON payload to inspect and potentially correct
     * @return Optional containing the corrected payload if double serialization was detected and corrected,
     *         otherwise empty Optional
     */
    private Optional<Object> correctIfDetected(Object payload) {
        try {
            // Convert payload to JsonNode for easier manipulation
            var rootNode = OBJECT_MAPPER.valueToTree(payload);
            var correctedNode = processNode(rootNode);

            // Check if any corrections were made
            if (!rootNode.equals(correctedNode)) {
                LOGGER.info("JSON double serialization detected and corrected.");
                var correctedPayload = OBJECT_MAPPER.treeToValue(correctedNode, Object.class);
                return Optional.of(correctedPayload);
            }

            return Optional.empty();
        } catch (RuntimeException | JsonProcessingException e) {
            LOGGER.debug("Failed to process payload for double serialization detection: {}.", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Recursively processes JSON nodes to detect and unwrap double-serialized strings.
     */
    private JsonNode processNode(JsonNode node) {
        if (node.isObject()) {
            return processObjectNode((ObjectNode) node);
        } else if (node.isArray()) {
            return processArrayNode((ArrayNode) node);
        } else if (node.isTextual()) {
            return processTextNode((TextNode) node);
        }
        return node;
    }

    /**
     * Processes object nodes, recursively checking each field.
     */
    private JsonNode processObjectNode(ObjectNode objectNode) {
        var result = OBJECT_MAPPER.createObjectNode();
        for (var field : objectNode.properties()) {
            var processedValue = processNode(field.getValue());
            result.set(field.getKey(), processedValue);
        }
        return result;
    }

    /**
     * Processes array nodes, recursively checking each element.
     */
    private JsonNode processArrayNode(ArrayNode arrayNode) {
        var result = OBJECT_MAPPER.createArrayNode();
        for (var element : arrayNode) {
            var processedElement = processNode(element);
            result.add(processedElement);
        }
        return result;
    }

    /**
     * Processes text nodes to detect and unwrap double-serialized JSON.
     */
    private JsonNode processTextNode(TextNode textNode) {
        var textValue = textNode.textValue();

        // Check if it looks like JSON (starts and ends with braces/brackets after trimming)
        var trimmed = textValue.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                var parsedNode = OBJECT_MAPPER.readTree(textValue);
                return processNode(parsedNode);
            } catch (JsonProcessingException e) {
                // Not valid JSON, keep as string
                return textNode;
            }
        }

        return textNode;
    }
}
