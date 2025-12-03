package com.infobip.openapi.mcp.openapi.schema;

import static java.lang.Boolean.TRUE;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData.ParametersByType;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composes a unified input schema for an OpenAPI operation by combining parameter schemas
 * and request body schema into a single JSON schema.
 * <p>
 * The composed schema has the following structure:
 * <pre>
 * {
 *   "type": "object",
 *   "properties": {
 *     "requestParameters": { ... }, // Combined parameters schema (configurable key)
 *     "requestSchema": { ... }      // Request body schema (configurable key)
 *   }
 * }
 * </pre>
 * <p>
 * If only parameters or only request body is present, the composed schema will contain
 * only that part. If neither is present, the result will be null.
 * <p>
 * The class also provides a method to decompose an input JSON back into its parameter
 * and request body components, which is the reverse operation of the compose method.
 * <p>
 * Currently, only query, path, header, and cookie parameters are supported.
 * Also, only application/json content type is supported for request bodies.
 */
@NullMarked
public class InputSchemaComposer {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputSchemaComposer.class);

    private static final Set<String> SUPPORTED_PARAMETER_TYPES =
            Set.of(ParametersByType.QUERY, ParametersByType.PATH, ParametersByType.HEADER, ParametersByType.COOKIE);

    private final String parametersKey;
    private final String requestBodyKey;

    public InputSchemaComposer(OpenApiMcpProperties.Tools.Schema properties) {
        this.parametersKey = properties.parametersKey();
        this.requestBodyKey = properties.requestBodyKey();
    }

    /**
     * Composes a unified input schema for the given operation by combining parameter schemas
     * and request body schema into a single JSON schema.
     * <p>
     * Currently, we use Swagger's {@link Schema} model to represent JSON schemas.
     * However, OpenAPI schemas are a superset of JSON Schema, so in the future we should
     * consider using a JSON schema model directly.
     * <p>
     * IMPORTANT NOTICE: Composing assumes that the Operation has already been resolved.
     * That means all $ref references should be resolved to their actual definitions.
     *
     * @param fullOperation The full OpenAPI operation (including spec version) to compose the input schema for.
     * @return A Schema representing the composed input schema, or null if neither parameters
     * nor request body are present.
     */
    public @Nullable Schema<?> compose(FullOperation fullOperation) {
        var parameterSchema = resolveParameterSchema(fullOperation);
        var requestBodySchema = resolveRequestBodySchema(fullOperation);

        if (parameterSchema != null) {
            if (requestBodySchema != null) {
                // Both parameters and request body exist - wrap both in their respective keys
                var combinedSchema = new ObjectSchema();
                combinedSchema.addProperty(parametersKey, parameterSchema);

                // Unwrap the request body if it was wrapped, then wrap it under requestBodyKey
                var unwrappedBodySchema = unwrapIfNeeded(requestBodySchema);
                combinedSchema.addProperty(requestBodyKey, unwrappedBodySchema);
                combinedSchema.required(List.of(parametersKey, requestBodyKey));
                return combinedSchema;
            }
            return parameterSchema;
        }

        return requestBodySchema;
    }

    /**
     * Decompose an input JSON back into its parameter and request body components.
     * This is the reverse operation of {@link InputSchemaComposer#compose} method.
     * TODO: add support for form parameters and multipart/form-data requests.
     * <p>
     *
     * @param callToolRequest The MCP CallToolRequest containing the input JSON to decompose.
     * @param operation       The operation for which the input is being decomposed.
     * @return A DecomposedSchema object containing the decomposed parameters and request body.
     */
    public DecomposedRequestData decompose(McpSchema.@Nullable CallToolRequest callToolRequest, Operation operation) {
        if (callToolRequest == null) {
            return DecomposedRequestData.empty();
        }

        // Use operation to guide decomposition
        var hasParameters =
                operation.getParameters() != null && !operation.getParameters().isEmpty();
        var hasRequestBody = getSupportedRequestBodySchema(operation.getRequestBody()) != null;
        var arguments = callToolRequest.arguments();

        boolean isWrappedFormat = arguments.containsKey(parametersKey) || arguments.containsKey(requestBodyKey);

        if (isWrappedFormat) {
            Map<String, Object> parameters = null;
            Object requestBody = null;

            if (arguments.containsKey(parametersKey)) {
                if (!hasParameters) {
                    LOGGER.warn(
                            "Parameters are not defined in the operation, but '{}' key is present in the input arguments."
                                    + " Skipping passed parameters. Please verify if this is the intended behavior.",
                            parametersKey);
                } else if (!(arguments.get(parametersKey) instanceof Map)) {
                    LOGGER.warn(
                            "Expected '{}' to be a Map when decomposing the input arguments. Skipping parameters.",
                            parametersKey);
                } else {
                    try {
                        @SuppressWarnings("unchecked")
                        var parametersMap = (Map<String, Object>) arguments.get(parametersKey);
                        parameters = parametersMap;
                    } catch (ClassCastException exception) {
                        LOGGER.warn("Failed to cast '{}' to Map<String, Object>. Skipping parameters.", parametersKey);
                    }
                }
            }

            if (arguments.containsKey(requestBodyKey)) {
                if (!hasRequestBody) {
                    LOGGER.warn(
                            "Request body is not defined in the operation, but '{}' key is present in the input arguments."
                                    + " Skipping passed request body. Please verify if this is the intended behavior.",
                            requestBodyKey);
                } else {
                    // For wrapped non-object schemas (string, array, etc.), accept any type
                    requestBody = arguments.get(requestBodyKey);
                }
            }

            return DecomposedRequestData.withParametersAndBodyContent(
                    organizeParametersByType(parameters, operation), requestBody);
        }

        // Handle direct format (no wrapper keys - determine by operation definition)
        if (hasParameters && !hasRequestBody) {
            return DecomposedRequestData.withParameters(organizeParametersByType(arguments, operation));
        }

        if (!hasParameters && hasRequestBody) {
            return DecomposedRequestData.withRequestBody(arguments);
        }

        /*
         * If both parameters and request body are present, or neither is present,
         * we cannot reliably determine the schema type. The composition logic ensures
         * that if both are present, they are wrapped in their respective keys.
         */
        LOGGER.warn("Cannot reliably determine schema type for operation."
                + " Both parameters and request body exist, or neither exists."
                + " Returning blank model.");
        return DecomposedRequestData.empty();
    }

    /**
     * Organizes parameters by their type (query, path, header, cookie).
     * This method is used to decompose the input JSON into its parameter components.
     *
     * @param parameters The parameters to organize.
     * @return A ParametersByType object containing the organized parameters.
     */
    private ParametersByType organizeParametersByType(@Nullable Map<String, Object> parameters, Operation operation) {
        if (parameters == null || parameters.isEmpty()) {
            return ParametersByType.empty();
        }

        if (operation.getParameters() == null) {
            return ParametersByType.empty();
        }

        var pathParameters = new HashMap<String, Object>();
        var queryParameters = new HashMap<String, Object>();
        var headerParameters = new HashMap<String, Object>();
        var cookieParameters = new HashMap<String, Object>();

        operation.getParameters().forEach(parameter -> {
            var parameterName = parameter.getName();
            var parameterIn = parameter.getIn();
            var inputValue = parameters.get(parameterName);
            if (inputValue != null) {
                switch (parameterIn) {
                    case ParametersByType.PATH -> pathParameters.put(parameterName, inputValue);
                    case ParametersByType.QUERY -> queryParameters.put(parameterName, inputValue);
                    case ParametersByType.HEADER -> headerParameters.put(parameterName, inputValue);
                    case ParametersByType.COOKIE -> cookieParameters.put(parameterName, inputValue);
                    default ->
                        LOGGER.warn(
                                "Unsupported parameter type '{}' for parameter '{}'."
                                        + " Skipping parameter decomposition.",
                                parameterIn,
                                parameterName);
                }
            }
        });

        return new ParametersByType(pathParameters, queryParameters, headerParameters, cookieParameters);
    }

    /**
     * Resolves the schema for parameters in an operation.
     * The method supports query, path, header and cookie parameters, and combines them
     * in a single json schema.
     * TODO: Form parameters are not supported yet.
     *
     * @param fullOperation The operation to resolve the parameters for.
     * @return An ObjectSchema containing the resolved parameters, or null if no parameters are resolved.
     */
    private @Nullable ObjectSchema resolveParameterSchema(FullOperation fullOperation) {
        var operation = fullOperation.operation();
        if (operation.getParameters() == null || operation.getParameters().isEmpty()) {
            return null;
        }

        var inputSchema = new ObjectSchema();
        for (var parameter : operation.getParameters()) {
            var parameterName = parameter.getName();
            var parameterIn = parameter.getIn();
            if (SUPPORTED_PARAMETER_TYPES.contains(parameterIn)) {
                var originalSchema = parameter.getSchema();
                Schema<?> schemaToUse;

                // Clone the schema if we need to modify it to avoid mutating the original
                if (originalSchema.getDescription() == null && parameter.getDescription() != null) {
                    schemaToUse = cloneSchema(originalSchema, fullOperation.openApi());
                    schemaToUse.setDescription(parameter.getDescription());
                } else {
                    schemaToUse = originalSchema;
                }

                inputSchema.addProperty(parameterName, schemaToUse);
                if (TRUE.equals(parameter.getRequired())) {
                    inputSchema.addRequiredItem(parameterName);
                }
            } else {
                LOGGER.warn(
                        "Unsupported parameter type '{}' for parameter '{}'. Skipping.", parameterIn, parameterName);
            }
        }

        if (inputSchema.getProperties() == null || inputSchema.getProperties().isEmpty()) {
            return null;
        }
        return inputSchema;
    }

    /**
     * Resolves the schema for the request body in an operation.
     * TODO: Only application/json content type is supported for now.
     *
     * @param fullOperation The operation to resolve the request body for.
     * @return A Schema containing the resolved request body, or null if no request body is resolved.
     */
    private @Nullable Schema<?> resolveRequestBodySchema(FullOperation fullOperation) {
        var operation = fullOperation.operation();
        var requestBody = operation.getRequestBody();
        if (requestBody == null) {
            return null;
        }

        var originalSchema = getSupportedRequestBodySchema(requestBody);
        if (originalSchema == null) {
            LOGGER.warn(
                    "Unsupported content types in request body for operation '{}'. Skipping.",
                    operation.getOperationId());
            return null;
        }

        // Check if schema is an object type with properties
        var isObjectType = "object".equals(originalSchema.getType())
                || (originalSchema.getTypes() != null
                        && originalSchema.getTypes().contains("object"));
        var hasProperties = originalSchema.getProperties() != null
                && !originalSchema.getProperties().isEmpty();

        if (isObjectType && hasProperties) {
            // Object schema with properties - return as-is (or with description added)
            // Clone the schema if we need to modify it to avoid mutating the original
            if (originalSchema.getDescription() == null && requestBody.getDescription() != null) {
                var clonedSchema = cloneSchema(originalSchema, fullOperation.openApi());
                clonedSchema.setDescription(requestBody.getDescription());
                return clonedSchema;
            } else {
                return originalSchema;
            }
        } else {
            // Non-object schema OR object schema without properties (e.g., composed schemas)
            // Wrap in ObjectSchema with the configured body key
            LOGGER.warn(
                    "Request body schema is not an ObjectSchema with properties for operation '{}'. Wrapping in ObjectSchema.",
                    operation.getOperationId());
            var objectSchema = new ObjectSchema();
            objectSchema.setDescription(requestBody.getDescription());
            objectSchema.addProperty(requestBodyKey, originalSchema);
            objectSchema.addRequiredItem(requestBodyKey);
            return objectSchema;
        }
    }

    /**
     * Unwraps a request body schema if it was wrapped in an ObjectSchema with the requestBodyKey.
     * This handles the case where resolveRequestBodySchema wrapped a non-object schema,
     * but we need to unwrap it when combining with parameters.
     *
     * @param schema The schema to potentially unwrap.
     * @return The unwrapped schema, or the original if it wasn't wrapped.
     */
    private Schema<?> unwrapIfNeeded(Schema<?> schema) {
        if (schema instanceof ObjectSchema objectSchema
                && objectSchema.getProperties() != null
                && objectSchema.getProperties().size() == 1
                && objectSchema.getProperties().containsKey(requestBodyKey)) {
            // This was wrapped by resolveRequestBodySchema - unwrap it
            return objectSchema.getProperties().get(requestBodyKey);
        }
        return schema;
    }

    /**
     * Clones a schema to avoid mutating the original when modifications are needed.
     *
     * @param originalSchema The schema to clone.
     * @param openApi        The OpenAPI specification containing version information for cloning.
     * @return A cloned copy of the schema.
     */
    private Schema<?> cloneSchema(Schema<?> originalSchema, OpenAPI openApi) {
        boolean isOpenApi31 = openApi.getSpecVersion() == SpecVersion.V31;
        return AnnotationsUtils.clone(originalSchema, isOpenApi31);
    }

    /**
     * Gets the schema from the request body if it has a supported media type.
     *
     * @param requestBody The request body to check for supported request body schema.
     * @return The raw Schema from the MediaType, or null if the request body doesn't have a supported media type.
     */
    private @Nullable Schema<?> getSupportedRequestBodySchema(@Nullable RequestBody requestBody) {
        return Optional.ofNullable(requestBody)
                .map(RequestBody::getContent)
                .map(content -> content.get(DecomposedRequestData.SUPPORTED_MEDIA_TYPE.toString()))
                .map(MediaType::getSchema)
                .orElse(null);
    }
}
