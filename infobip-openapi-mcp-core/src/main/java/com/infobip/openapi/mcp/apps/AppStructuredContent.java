package com.infobip.openapi.mcp.apps;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/**
 * Builds the {@code structuredContent} of a tool result for MCP Apps.
 *
 * <p>MCP Apps UIs receive tool results as JSON objects. A tool whose declared response schema is a
 * JSON object gets the parsed body as-is; a tool whose declared schema is an array or a scalar gets
 * the parsed body wrapped as {@code {"result": <value>}}. When the schema does not determine the
 * shape, the runtime value decides it. Wrapping is also applied whenever the runtime value is not a
 * JSON object, so {@code structuredContent} is always a JSON object.
 *
 * <p>The declared shape is inspected once at registration time (see {@link #declaredSchemaKind}) so
 * that the result shape is stable per tool and predictable for UI authors.
 */
@NullMarked
public final class AppStructuredContent {

    /**
     * The key under which a non-object result value is wrapped.
     */
    public static final String RESULT_WRAPPER_KEY = "result";

    private static final String PREFERRED_SUCCESS_RESPONSE_CODE = "200";
    private static final String SUCCESS_RESPONSE_RANGE = "2XX";
    private static final Set<String> SCALAR_TYPES = Set.of("string", "number", "integer", "boolean");

    private AppStructuredContent() {}

    /**
     * Determines the declared response shape of an operation from the schema of the best successful
     * response that declares a JSON media type ({@code 200} preferred, then any other explicit
     * {@code 2xx} code in ascending order, then the {@code 2XX} range key). Returns
     * {@link DeclaredSchemaKind#UNKNOWN} when no such response or schema exists, or when the
     * declared schema is not determinable.
     */
    public static DeclaredSchemaKind declaredSchemaKind(Operation operation) {
        var responses = operation.getResponses();
        if (responses == null) {
            return DeclaredSchemaKind.UNKNOWN;
        }

        return responses.entrySet().stream()
                .filter(entry -> successResponsePriority(entry.getKey()) >= 0)
                .filter(entry -> entry.getValue() != null)
                .sorted(Comparator.comparingInt(entry -> successResponsePriority(entry.getKey())))
                .map(entry -> jsonSchemaOf(entry.getValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .map(AppStructuredContent::classify)
                .orElse(DeclaredSchemaKind.UNKNOWN);
    }

    /**
     * Composes the {@code structuredContent} value for a parsed JSON response body according to the
     * declared schema kind. Returns {@code null} when there is no value to publish.
     */
    public static @Nullable Object compose(DeclaredSchemaKind declaredSchemaKind, @Nullable Object parsedJsonValue) {
        if (parsedJsonValue == null) {
            return null;
        }
        return switch (declaredSchemaKind) {
            case OBJECT -> parsedJsonValue instanceof Map<?, ?> objectValue ? objectValue : wrap(parsedJsonValue);
            case NON_OBJECT -> wrap(parsedJsonValue);
            case UNKNOWN -> parsedJsonValue instanceof Map<?, ?> objectValue ? objectValue : wrap(parsedJsonValue);
        };
    }

    /**
     * Returns whether the given media type carries JSON, including {@code +json} suffixed and
     * wildcard media types.
     */
    public static boolean isJsonMediaType(MediaType mediaType) {
        return mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)
                || mediaType.getSubtype().endsWith("+json");
    }

    /**
     * Returns whether the given media type string from an OpenAPI response content map carries JSON.
     */
    public static boolean isJsonMediaType(String mediaTypeString) {
        try {
            return isJsonMediaType(MediaType.parseMediaType(mediaTypeString));
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static Map<String, Object> wrap(Object value) {
        return Map.of(RESULT_WRAPPER_KEY, value);
    }

    static DeclaredSchemaKind classify(Schema<?> schema) {
        if (schema.get$ref() != null) {
            // Unresolved references should not occur: the specification is fully resolved before
            // registration, and a shape that cannot be read cannot be trusted.
            return DeclaredSchemaKind.UNKNOWN;
        }

        var types = declaredTypes(schema);
        if (!types.isEmpty()) {
            if (types.contains("object")) {
                return types.size() == 1 ? DeclaredSchemaKind.OBJECT : DeclaredSchemaKind.UNKNOWN;
            }
            if (types.stream().allMatch(type -> "array".equals(type) || SCALAR_TYPES.contains(type))) {
                // Arrays and scalars are wrapped identically, so any mix of them is still NON_OBJECT.
                return DeclaredSchemaKind.NON_OBJECT;
            }
            return DeclaredSchemaKind.UNKNOWN;
        }

        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return DeclaredSchemaKind.OBJECT;
        }
        if (schema.getItems() != null) {
            return DeclaredSchemaKind.NON_OBJECT;
        }

        var combinatorKinds = Stream.of(schema.getAllOf(), schema.getOneOf(), schema.getAnyOf())
                .filter(Objects::nonNull)
                .flatMap(members -> members.stream())
                .filter(Objects::nonNull)
                .map(AppStructuredContent::classify)
                .toList();
        if (combinatorKinds.isEmpty()) {
            return DeclaredSchemaKind.UNKNOWN;
        }
        if (combinatorKinds.stream().allMatch(kind -> kind == DeclaredSchemaKind.OBJECT)) {
            return DeclaredSchemaKind.OBJECT;
        }
        if (combinatorKinds.stream().allMatch(kind -> kind == DeclaredSchemaKind.NON_OBJECT)) {
            return DeclaredSchemaKind.NON_OBJECT;
        }
        return DeclaredSchemaKind.UNKNOWN;
    }

    private static Set<String> declaredTypes(Schema<?> schema) {
        // OpenAPI 3.0 stores the type in `type`, OpenAPI 3.1 may store a union in `types`; the
        // nullable-union normalizer has already removed "null" members by this point.
        var types = new LinkedHashSet<String>();
        if (schema.getType() != null) {
            types.add(schema.getType());
        }
        if (schema.getTypes() != null) {
            types.addAll(schema.getTypes());
        }
        types.remove("null");
        return types;
    }

    private static @Nullable Schema<?> jsonSchemaOf(ApiResponse response) {
        var content = response.getContent();
        if (content == null) {
            return null;
        }
        return content.entrySet().stream()
                .filter(entry -> isJsonMediaType(entry.getKey()))
                .map(entry -> entry.getValue().getSchema())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static int successResponsePriority(String responseCode) {
        if (PREFERRED_SUCCESS_RESPONSE_CODE.equals(responseCode)) {
            return 0;
        }
        if (SUCCESS_RESPONSE_RANGE.equalsIgnoreCase(responseCode)) {
            return 1000;
        }
        if (responseCode.length() == 3
                && responseCode.charAt(0) == '2'
                && responseCode.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(responseCode);
        }
        return -1;
    }
}
