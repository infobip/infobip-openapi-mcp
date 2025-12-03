package com.infobip.openapi.mcp.openapi.schema;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class InputSchemaComposerTest {

    private final InputSchemaComposer composer =
            new InputSchemaComposer(new OpenApiMcpProperties.Tools.Schema(null, null));

    @Nested
    @DisplayName("compose method")
    class ComposeMethod {

        @Test
        void shouldReturnNullWhenOperationIsEmpty() {
            // given
            var operation = new Operation();

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNull();
        }

        @Test
        void shouldReturnParameterSchemaOnlyWhenOnlyParametersExist() throws Exception {
            // given
            var operation = createOperationWithParameters();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(3);
            then(schema.getProperties()).containsKeys("userId", "limit", "Authorization");
            then(schema.getRequired()).containsExactly("userId");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "userId": {
                          "type": "string",
                          "description": "The user ID"
                        },
                        "limit": {
                          "type": "integer",
                          "format": "int32",
                          "description": "The limit parameter"
                        },
                        "Authorization": {
                          "type": "string",
                          "description": "Authorization header"
                        }
                      },
                      "required": ["userId"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldReturnRequestBodySchemaOnlyWhenOnlyRequestBodyExists() throws Exception {
            // given
            var operation = createOperationWithRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(2);
            then(schema.getProperties()).containsKeys("name", "email");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "name": {
                          "type": "string"
                        },
                        "email": {
                          "type": "string"
                        }
                      },
                      "description": "User data"
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldCombineBothSchemasWhenBothExist() throws Exception {
            // given
            var operation = createOperationWithParametersAndRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(2);
            then(schema.getProperties()).containsKeys("_params", "_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_params": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "string",
                              "description": "The user ID"
                            }
                          },
                          "required": ["userId"]
                        },
                        "_body": {
                          "type": "object",
                          "properties": {
                            "name": {
                              "type": "string"
                            },
                            "email": {
                              "type": "string"
                            }
                          },
                          "description": "User data"
                        }
                      },
                      "required": ["_params", "_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldSkipUnsupportedParameterTypes(CapturedOutput output) {
            // given
            var operation = new Operation();
            var formParameter = new Parameter()
                    .name("formParam")
                    .in("form")
                    .schema(new StringSchema())
                    .required(true);
            var queryParameter = new Parameter().name("queryParam").in("query").schema(new StringSchema());

            operation.setParameters(List.of(formParameter, queryParameter));

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(1);
            then(schema.getProperties()).containsKey("queryParam");
            then(schema.getProperties()).doesNotContainKey("formParam");

            // Verify that warning was logged for unsupported parameter type
            then(output.getOut()).contains("Unsupported parameter type 'form' for parameter 'formParam'. Skipping.");
        }

        @Test
        void shouldWrapNonObjectRequestBody() throws Exception {
            // given
            var operation = createOperationWithStringRequestBodyWrapper();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(1);
            then(schema.getProperties()).containsKey("_body");

            // Verify JSON representation
            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_body": {
                          "type": "string",
                          "description": "A simple string"
                        }
                      },
                      "description": "String request body",
                      "required": ["_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldHandleUnsupportedMediaType(CapturedOutput output) {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();
            mediaType.setSchema(new StringSchema());
            content.addMediaType("text/plain", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);
            operation.setOperationId("operationWithUnsupportedMediaType");

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNull();

            // Verify that warning was logged for unsupported media type
            then(output.getOut())
                    .contains(
                            "Unsupported content types in request body for operation 'operationWithUnsupportedMediaType'. Skipping.");
        }

        @Test
        void shouldCopyParameterDescriptionToSchema() throws Exception {
            // given
            var operation = new Operation();
            var originalParameterSchema = new StringSchema();
            var parameter = new Parameter()
                    .name("testParam")
                    .in("query")
                    .description("Parameter description")
                    .schema(originalParameterSchema); // schema without description

            operation.setParameters(List.of(parameter));

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            var paramSchema = schema.getProperties().get("testParam");
            then(paramSchema).isNotSameAs(originalParameterSchema);
            then(paramSchema.getDescription()).isEqualTo("Parameter description");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "testParam": {
                          "type": "string",
                          "description": "Parameter description"
                        }
                      }
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldPreserveSchemaDescriptionOverParameterDescription() throws Exception {
            // given
            var operation = new Operation();
            var stringSchema = new StringSchema();
            stringSchema.setDescription("Schema description");
            var parameter = new Parameter()
                    .name("testParam")
                    .in("query")
                    .description("Parameter description")
                    .schema(stringSchema);

            operation.setParameters(List.of(parameter));

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            var paramSchema = schema.getProperties().get("testParam");
            then(paramSchema).isSameAs(stringSchema);
            then(paramSchema.getDescription()).isEqualTo("Schema description");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "testParam": {
                          "type": "string",
                          "description": "Schema description"
                        }
                      }
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldHandleRequestBodyWithNullContent() {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            requestBody.setContent(null);
            operation.setRequestBody(requestBody);

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNull();
        }

        @Test
        void shouldHandleRequestBodyWithNullSchema() {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();
            mediaType.setSchema(null);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);
            operation.setOperationId("operationWithNullSchema");

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNull();
        }

        @Test
        void shouldHandleObjectSchemaWithNullDescription() throws Exception {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ObjectSchema();
            schema.addProperty("name", new StringSchema());
            schema.setDescription(null); // explicitly null description

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("Request body description");
            operation.setRequestBody(requestBody);

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNotNull();
            then(result).isNotSameAs(schema);
            then(result.getType()).isEqualTo("object");
            then(result.getDescription()).isEqualTo("Request body description");

            var json = jsonRepresentation(result);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "name": {
                          "type": "string"
                        }
                      },
                      "description": "Request body description"
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldPreserveObjectSchemaWithExistingDescription() throws Exception {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ObjectSchema();
            schema.addProperty("name", new StringSchema());
            schema.setDescription("Existing schema description");

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("Request body description");
            operation.setRequestBody(requestBody);

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNotNull();
            then(result).isSameAs(schema);
            then(result.getType()).isEqualTo("object");
            then(result.getDescription()).isEqualTo("Existing schema description");

            var json = jsonRepresentation(result);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "name": {
                          "type": "string"
                        }
                      },
                      "description": "Existing schema description"
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldCloneSchemaForOpenAPI30Spec() throws Exception {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var originalSchema = new ObjectSchema();
            originalSchema.addProperty("name", new StringSchema());
            originalSchema.addProperty("email", new StringSchema());
            originalSchema.contentMediaType("application/json"); // OpenAPI 3.1 specific field

            mediaType.setSchema(originalSchema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("Request body description");
            operation.setRequestBody(requestBody);

            // Create OpenAPI 3.0 spec (not 3.1)
            var openApi30 = new OpenAPI().specVersion(SpecVersion.V30).openapi("3.0.1");

            // when
            var result = composer.compose(new FullOperation("/test", PathItem.HttpMethod.POST, operation, openApi30));

            // then - Should return cloned schema, not modify original
            then(result).isNotNull();
            then(result).isNotSameAs(originalSchema); // Verify it's a different instance (cloned)
            then(result.getDescription()).isEqualTo("Request body description");
            then(result.getProperties()).hasSize(2);
            then(result.getProperties()).containsKeys("name", "email");

            // Verify original schema remains unchanged
            then(originalSchema.getDescription()).isNull();
            then(originalSchema.getProperties()).hasSize(2);

            var json = jsonRepresentation(result);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "name": {
                          "type": "string"
                        },
                        "email": {
                          "type": "string"
                        }
                      },
                      "description": "Request body description"
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldHandleSchemaWithTypesArrayContainingObject() {
            // given
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ObjectSchema();
            schema.addProperty("name", new StringSchema());
            schema.setTypes(Set.of("object", "null")); // types array containing "object"
            schema.setDescription(null);

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("Request body with types array");
            operation.setRequestBody(requestBody);

            // when
            var result = composer.compose(createFullOperation(operation));

            // then
            then(result).isNotNull();
            then(result).isInstanceOf(ObjectSchema.class);
            then(result.getDescription()).isEqualTo("Request body with types array");
        }
    }

    @Nested
    @DisplayName("decompose method")
    class DecomposeMethod {

        @Test
        void shouldReturnEmptyWhenCallToolRequestIsNull() {
            // given
            var operation = new Operation();

            // when
            var result = composer.decompose(null, operation);

            // then
            then(result).isEqualTo(DecomposedRequestData.empty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldDecomposeCombinedParametersAndRequestBody() {
            // given
            var operation = createOperationWithParametersAndRequestBody();
            var callToolRequest = createCallToolRequestWithBothParametersAndRequestBody();

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).isEmpty();
            then(result.parametersByType().header()).isEmpty();
            then(result.parametersByType().cookie()).isEmpty();

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John Doe"), entry("email", "john@example.com"));
        }

        @Test
        void shouldDecomposeParametersOnlyWhenOperationHasOnlyParameters() {
            // given
            var operation = createOperationWithParameters();
            var callToolRequest = createCallToolRequestWithParameters();

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).containsOnly(entry("limit", 10));
            then(result.parametersByType().header()).containsOnly(entry("Authorization", "Bearer token"));
            then(result.parametersByType().cookie()).isEmpty();

            then(result.requestBody()).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldDecomposeRequestBodyOnlyWhenOperationHasOnlyRequestBody() {
            // given
            var operation = createOperationWithRequestBody();
            var callToolRequest = createCallToolRequestWithRequestBody();

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John Doe"), entry("email", "john@example.com"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldHandleInvalidParameterTypesGracefully(CapturedOutput output) {
            // given
            var operation = createOperationWithParametersAndRequestBody();
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", "invalid-not-a-map");
            arguments.put("_body", Map.of("name", "John"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content()).containsOnly(entry("name", "John"));

            // Verify that warning was logged for invalid parameter type
            then(output.getOut())
                    .contains(
                            "Expected '_params' to be a Map when decomposing the input arguments. Skipping parameters.");
        }

        @Test
        void shouldReturnEmptyWhenCannotDetermineSchemaType(CapturedOutput output) {
            // given
            var operation = createOperationWithParametersAndRequestBody();
            var arguments = new HashMap<String, Object>();
            arguments.put("unknownKey", "unknownValue");
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isEqualTo(DecomposedRequestData.empty());

            // Verify that warning was logged for unknown keys
            then(output.getOut())
                    .contains("Cannot reliably determine schema type for operation."
                            + " Both parameters and request body exist, or neither exists. Returning blank model.");
        }

        @Test
        void shouldOrganizeParametersByTypeCorrectly() {
            // given
            var operation = createOperationWithAllParameterTypes();
            var arguments = new HashMap<String, Object>();
            arguments.put("userId", "123");
            arguments.put("limit", 10);
            arguments.put("Authorization", "Bearer token");
            arguments.put("sessionId", "abc123");
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).containsOnly(entry("limit", 10));
            then(result.parametersByType().header()).containsOnly(entry("Authorization", "Bearer token"));
            then(result.parametersByType().cookie()).containsOnly(entry("sessionId", "abc123"));
        }

        @Test
        void shouldOrganizeMultipleParametersOfSameTypeCorrectly() {
            // given
            var operation = new Operation();
            var queryParam1 = new Parameter().name("limit").in("query").schema(new IntegerSchema());
            var queryParam2 = new Parameter().name("offset").in("query").schema(new IntegerSchema());
            var queryParam3 = new Parameter().name("sort").in("query").schema(new StringSchema());
            var headerParam1 =
                    new Parameter().name("Authorization").in("header").schema(new StringSchema());
            var headerParam2 = new Parameter().name("X-API-Key").in("header").schema(new StringSchema());

            operation.setParameters(List.of(queryParam1, queryParam2, queryParam3, headerParam1, headerParam2));

            var arguments = new HashMap<String, Object>();
            arguments.put("limit", 10);
            arguments.put("offset", 20);
            arguments.put("sort", "name");
            arguments.put("Authorization", "Bearer token");
            arguments.put("X-API-Key", "api-key-123");
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType().path()).isEmpty();
            then(result.parametersByType().query())
                    .containsOnly(entry("limit", 10), entry("offset", 20), entry("sort", "name"));
            then(result.parametersByType().header())
                    .containsOnly(entry("Authorization", "Bearer token"), entry("X-API-Key", "api-key-123"));
            then(result.parametersByType().cookie()).isEmpty();
        }

        @Test
        void shouldHandleMissingParametersInInput() {
            // given
            var operation = createOperationWithParameters();
            var arguments = new HashMap<String, Object>();
            arguments.put("userId", "123"); // missing limit and Authorization
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).isEmpty(); // limit not provided
            then(result.parametersByType().header()).isEmpty(); // Authorization not provided
        }

        @Test
        void shouldHandleUnsupportedParameterTypesDuringDecomposition(CapturedOutput output) {
            // given
            var operation = new Operation();
            var formParameter = new Parameter().name("formParam").in("form").schema(new StringSchema());
            var queryParameter = new Parameter().name("queryParam").in("query").schema(new StringSchema());

            operation.setParameters(List.of(formParameter, queryParameter));

            var arguments = new HashMap<String, Object>();
            arguments.put("formParam", "formValue");
            arguments.put("queryParam", "queryValue");
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType().query()).containsOnly(entry("queryParam", "queryValue"));
            then(result.parametersByType().path()).isEmpty();
            then(result.parametersByType().header()).isEmpty();
            then(result.parametersByType().cookie()).isEmpty();
            // formParam should be ignored as it's unsupported

            // Verify that warning was logged for unsupported parameter type during decomposition
            then(output.getOut())
                    .contains(
                            "Unsupported parameter type 'form' for parameter 'formParam'. Skipping parameter decomposition.");
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldHandleOperationWithNullParameters() {
            // given
            var operation = createOperationWithRequestBody();
            operation.setParameters(null);
            var arguments = new HashMap<String, Object>();
            arguments.put("name", "John");
            arguments.put("email", "john@example.com");
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John"), entry("email", "john@example.com"));
        }

        @Test
        void shouldDecomposeWhenOnlyRequestParametersKeyIsPresent() {
            // given - Operation with both parameters and request body
            var operation = createOperationWithParametersAndRequestBody();

            // Arguments contain only requestParameters key (no requestSchema key)
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose parameters even without requestSchema key
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).isEmpty();
            then(result.parametersByType().header()).isEmpty();
            then(result.parametersByType().cookie()).isEmpty();

            // Request body should be null since requestSchema key wasn't provided
            then(result.requestBody()).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldDecomposeWhenOnlyRequestSchemaKeyIsPresent() {
            // given - Operation with both parameters and request body
            var operation = createOperationWithParametersAndRequestBody();

            // Arguments contain only requestSchema key (no requestParameters key)
            var arguments = new HashMap<String, Object>();
            arguments.put(
                    "_body",
                    Map.of(
                            "name", "John Doe",
                            "email", "john@example.com"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose request body even without requestParameters key
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John Doe"), entry("email", "john@example.com"));
        }

        @Test
        void shouldHandleRequestParametersKeyWithOperationHavingOnlyParameters() {
            // given - Operation with only parameters (no request body)
            var operation = createOperationWithParameters();

            // Arguments use requestParameters key structure
            var arguments = new HashMap<String, Object>();
            arguments.put(
                    "_params",
                    Map.of(
                            "userId", "123",
                            "limit", 10,
                            "Authorization", "Bearer token"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose parameters from requestParameters key
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).containsOnly(entry("limit", 10));
            then(result.parametersByType().header()).containsOnly(entry("Authorization", "Bearer token"));
            then(result.parametersByType().cookie()).isEmpty();

            then(result.requestBody()).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldHandleRequestSchemaKeyWithOperationHavingOnlyRequestBody() {
            // given - Operation with only request body (no parameters)
            var operation = createOperationWithRequestBody();

            // Arguments use requestSchema key structure
            var arguments = new HashMap<String, Object>();
            arguments.put(
                    "_body",
                    Map.of(
                            "name", "John Doe",
                            "email", "john@example.com"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose request body from requestSchema key
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John Doe"), entry("email", "john@example.com"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyRequestParametersKey() {
            // given - Operation with both parameters and request body
            var operation = createOperationWithParametersAndRequestBody();

            // Arguments contain empty requestParameters and valid requestSchema
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of()); // empty parameters
            arguments.put(
                    "_body",
                    Map.of(
                            "name", "John Doe",
                            "email", "john@example.com"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should handle empty parameters gracefully
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John Doe"), entry("email", "john@example.com"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyRequestSchemaKey() {
            // given - Operation with both parameters and request body
            var operation = createOperationWithParametersAndRequestBody();

            // Arguments contain valid requestParameters and empty requestSchema
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123"));
            arguments.put("_body", Map.of()); // empty request body
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should handle empty request body gracefully
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content()).isEmpty();
        }

        @Test
        void shouldDecomposeWrappedStringSchemaCorrectly() {
            // given - Operation with string request body (gets wrapped during composition)
            var operation = createOperationWithStringRequestBody();

            // Arguments for wrapped string schema - when composer wraps non-object schemas
            var arguments = new HashMap<String, Object>();
            arguments.put("_body", "Hello World"); // Direct string value
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose the wrapped string schema
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo("Hello World");
        }

        @Test
        void shouldDecomposeWrappedArraySchemaCorrectly() {
            // given - Operation with array request body (gets wrapped during composition)
            var operation = createOperationWithArrayRequestBody();

            // Arguments for wrapped array schema
            var arguments = new HashMap<String, Object>();
            arguments.put("_body", List.of("item1", "item2", "item3")); // Direct array value
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose the wrapped array schema
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo(List.of("item1", "item2", "item3"));
        }

        @Test
        void shouldDecomposeWrappedNumberSchemaCorrectly() {
            // given - Operation with number request body (gets wrapped during composition)
            var operation = createOperationWithNumberRequestBody();

            // Arguments for wrapped number schema
            var arguments = new HashMap<String, Object>();
            arguments.put("_body", 42.5); // Direct number value
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose the wrapped number schema
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo(42.5);
        }

        @Test
        void shouldDecomposeWrappedBooleanSchemaCorrectly() {
            // given - Operation with boolean request body (gets wrapped during composition)
            var operation = createOperationWithBooleanRequestBody();

            // Arguments for wrapped boolean schema
            var arguments = new HashMap<String, Object>();
            arguments.put("_body", true); // Direct boolean value
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose the wrapped boolean schema
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo(true);
        }

        @Test
        void shouldDecomposeWrappedStringSchemaWithParameters() {
            // given - Operation with both parameters and string request body
            var operation = createOperationWithParametersAndStringRequestBody();

            // Arguments contain both requestParameters and wrapped string requestSchema
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123"));
            arguments.put("_body", "Hello World");
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose both parameters and wrapped string schema
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo("Hello World");
        }

        @Test
        void shouldDecomposeWrappedArraySchemaWithParameters() {
            // given - Operation with both parameters and array request body
            var operation = createOperationWithParametersAndArrayRequestBody();

            // Arguments contain both requestParameters and wrapped array requestSchema
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123"));
            arguments.put("_body", List.of("item1", "item2"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose both parameters and wrapped array schema
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo(List.of("item1", "item2"));
        }

        @Test
        void shouldHandleComplexArraySchemaDecomposition() {
            // given - Operation with complex array request body
            var operation = createOperationWithComplexArrayRequestBody();

            // Arguments for complex array with nested objects
            var arguments = new HashMap<String, Object>();
            arguments.put("_body", List.of(Map.of("id", 1, "name", "John"), Map.of("id", 2, "name", "Jane")));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should properly decompose the complex array schema
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content())
                    .isEqualTo(List.of(Map.of("id", 1, "name", "John"), Map.of("id", 2, "name", "Jane")));
        }

        @Test
        void shouldHandleNullValueInWrappedSchema() {
            // given - Operation with string request body that accepts null
            var operation = createOperationWithNullableStringRequestBody();

            // Arguments with null value for requestSchema
            var arguments = new HashMap<String, Object>();
            arguments.put("_body", null);
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then - Should handle null value gracefully
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNull();
        }

        @Test
        void shouldWarnWhenParametersKeyPresentButOperationHasNoParameters(CapturedOutput output) {
            // given
            var operation = createOperationWithRequestBody(); // Operation with only request body, no parameters
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123", "limit", 10)); // Parameters provided but not expected
            arguments.put("_body", Map.of("name", "John Doe", "email", "john@example.com"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();

            // Verify that warning was logged for parameters key being present when operation has no parameters
            then(output.getOut())
                    .contains(
                            "Parameters are not defined in the operation, but '_params' key is present in the input arguments."
                                    + " Skipping passed parameters. Please verify if this is the intended behavior.");
        }

        @Test
        void shouldWarnWhenRequestBodyKeyPresentButOperationHasNoRequestBody(CapturedOutput output) {
            // given
            var operation = createOperationWithParameters(); // Operation with only parameters, no request body
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123", "limit", 10));
            arguments.put(
                    "_body",
                    Map.of("name", "John Doe", "email", "john@example.com")); // Request body provided but not expected
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).containsOnly(entry("limit", 10));
            then(result.requestBody()).isNull();

            // Verify that warning was logged for request body key being present when operation has no request body
            then(output.getOut())
                    .contains(
                            "Request body is not defined in the operation, but '_body' key is present in the input arguments."
                                    + " Skipping passed request body. Please verify if this is the intended behavior.");
        }

        @Test
        void shouldWarnForBothKeysWhenOperationHasNeitherParametersNorRequestBody(CapturedOutput output) {
            // given
            var operation = new Operation(); // Empty operation with neither parameters nor request body
            var arguments = new HashMap<String, Object>();
            arguments.put("_params", Map.of("userId", "123"));
            arguments.put("_body", Map.of("name", "John Doe"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = composer.decompose(callToolRequest, operation);

            // then
            then(result).isEqualTo(DecomposedRequestData.empty());

            // Verify that both warnings were logged
            then(output.getOut())
                    .contains(
                            "Parameters are not defined in the operation, but '_params' key is present in the input arguments."
                                    + " Skipping passed parameters. Please verify if this is the intended behavior.");
            then(output.getOut())
                    .contains(
                            "Request body is not defined in the operation, but '_body' key is present in the input arguments."
                                    + " Skipping passed request body. Please verify if this is the intended behavior.");
        }
    }

    // Helper methods to create test data

    /**
     * Helper method to create a FullOperation with default OpenAPI 3.1 spec version for testing.
     * This ensures all tests use the correct OpenAPI version handling.
     */
    private FullOperation createFullOperation(Operation operation) {
        return new FullOperation("/test", PathItem.HttpMethod.GET, operation, new OpenAPI());
    }

    private Operation createOperationWithParameters() {
        var operation = new Operation();
        var pathParam = new Parameter()
                .name("userId")
                .in("path")
                .schema(new StringSchema())
                .description("The user ID")
                .required(true);
        var queryParam = new Parameter()
                .name("limit")
                .in("query")
                .schema(new IntegerSchema())
                .description("The limit parameter");
        var headerParam = new Parameter()
                .name("Authorization")
                .in("header")
                .schema(new StringSchema())
                .description("Authorization header");

        operation.setParameters(List.of(pathParam, queryParam, headerParam));
        return operation;
    }

    private Operation createOperationWithRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var schema = new ObjectSchema();
        schema.addProperty("name", new StringSchema());
        schema.addProperty("email", new StringSchema());

        mediaType.setSchema(schema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("User data");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithParametersAndRequestBody() {
        var operation = createOperationWithRequestBody();
        var pathParam = new Parameter()
                .name("userId")
                .in("path")
                .schema(new StringSchema())
                .description("The user ID")
                .required(true);
        operation.setParameters(List.of(pathParam));
        return operation;
    }

    private Operation createOperationWithAllParameterTypes() {
        var operation = new Operation();
        var pathParam = new Parameter()
                .name("userId")
                .in("path")
                .schema(new StringSchema())
                .required(true);
        var queryParam = new Parameter().name("limit").in("query").schema(new IntegerSchema());
        var headerParam = new Parameter().name("Authorization").in("header").schema(new StringSchema());
        var cookieParam = new Parameter().name("sessionId").in("cookie").schema(new StringSchema());

        operation.setParameters(List.of(pathParam, queryParam, headerParam, cookieParam));
        return operation;
    }

    private McpSchema.CallToolRequest createCallToolRequestWithParameters() {
        var arguments = new HashMap<String, Object>();
        arguments.put("userId", "123");
        arguments.put("limit", 10);
        arguments.put("Authorization", "Bearer token");
        return new McpSchema.CallToolRequest("test-tool", arguments);
    }

    private McpSchema.CallToolRequest createCallToolRequestWithRequestBody() {
        var arguments = new HashMap<String, Object>();
        arguments.put("name", "John Doe");
        arguments.put("email", "john@example.com");
        return new McpSchema.CallToolRequest("test-tool", arguments);
    }

    private McpSchema.CallToolRequest createCallToolRequestWithBothParametersAndRequestBody() {
        var arguments = new HashMap<String, Object>();
        arguments.put("_params", Map.of("userId", "123"));
        arguments.put(
                "_body",
                Map.of(
                        "name", "John Doe",
                        "email", "john@example.com"));
        return new McpSchema.CallToolRequest("test-tool", arguments);
    }

    /**
     * Helper method to convert a Schema to its JSON representation using Json31 mapper.
     *
     * @param schema The schema to convert to JSON
     * @return JSON string representation of the schema
     * @throws JsonProcessingException if JSON serialization fails
     */
    private String jsonRepresentation(Schema<?> schema) throws JsonProcessingException {
        return Json31.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(schema);
    }

    // Additional helper methods for creating operations with wrapped schemas

    private Operation createOperationWithStringRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var stringSchema = new StringSchema();
        stringSchema.setDescription("A simple string request body");

        mediaType.setSchema(stringSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("String request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithArrayRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var arraySchema = new ArraySchema();
        arraySchema.setItems(new StringSchema());
        arraySchema.setDescription("Array of strings");

        mediaType.setSchema(arraySchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("Array request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithNumberRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var numberSchema = new NumberSchema();
        numberSchema.setDescription("A number value");

        mediaType.setSchema(numberSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("Number request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithBooleanRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var booleanSchema = new BooleanSchema();
        booleanSchema.setDescription("A boolean value");

        mediaType.setSchema(booleanSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("Boolean request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithParametersAndStringRequestBody() {
        var operation = createOperationWithStringRequestBody();
        var pathParam = new Parameter()
                .name("userId")
                .in("path")
                .schema(new StringSchema())
                .description("The user ID")
                .required(true);
        operation.setParameters(List.of(pathParam));
        return operation;
    }

    private Operation createOperationWithParametersAndArrayRequestBody() {
        var operation = createOperationWithArrayRequestBody();
        var pathParam = new Parameter()
                .name("userId")
                .in("path")
                .schema(new StringSchema())
                .description("The user ID")
                .required(true);
        operation.setParameters(List.of(pathParam));
        return operation;
    }

    private Operation createOperationWithComplexArrayRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var arraySchema = new ArraySchema();
        var itemSchema = new ObjectSchema();
        itemSchema.addProperty("id", new IntegerSchema());
        itemSchema.addProperty("name", new StringSchema());
        arraySchema.setItems(itemSchema);
        arraySchema.setDescription("Array of user objects");

        mediaType.setSchema(arraySchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("Complex array request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithNullableStringRequestBody() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();

        var stringSchema = new StringSchema();
        stringSchema.setDescription("A nullable string");
        stringSchema.setNullable(true);

        mediaType.setSchema(stringSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("Nullable string request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    private Operation createOperationWithStringRequestBodyWrapper() {
        var operation = new Operation();
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();
        var stringSchema = new StringSchema();
        stringSchema.setDescription("A simple string");
        mediaType.setSchema(stringSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        requestBody.setDescription("String request body");
        operation.setRequestBody(requestBody);
        return operation;
    }

    @Nested
    @DisplayName("Composed Schema Wrapping")
    class ComposedSchemaWrappingTest {

        @Test
        @DisplayName("Should wrap oneOf composed schema without properties in body key")
        void shouldWrapOneOfSchemaWithoutProperties() throws Exception {
            // given
            var operation = createOperationWithOneOfRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(1);
            then(schema.getProperties()).containsKey("_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_body": {
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "type": { "type": "string", "enum": ["A"] },
                                "valueA": { "type": "string" }
                              }
                            },
                            {
                              "type": "object",
                              "properties": {
                                "type": { "type": "string", "enum": ["B"] },
                                "valueB": { "type": "integer", "format": "int32" }
                              }
                            }
                          ]
                        }
                      },
                      "description": "OneOf request body",
                      "required": ["_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should wrap allOf composed schema without properties in body key")
        void shouldWrapAllOfSchemaWithoutProperties() throws Exception {
            // given
            var operation = createOperationWithAllOfRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(1);
            then(schema.getProperties()).containsKey("_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_body": {
                          "allOf": [
                            {
                              "type": "object",
                              "properties": {
                                "id": { "type": "string" }
                              }
                            },
                            {
                              "type": "object",
                              "properties": {
                                "name": { "type": "string" }
                              }
                            }
                          ]
                        }
                      },
                      "description": "AllOf request body",
                      "required": ["_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should not wrap object schema with properties even if it has oneOf")
        void shouldNotWrapObjectSchemaWithPropertiesAndOneOf() throws Exception {
            // given
            var operation = createOperationWithObjectAndOneOfRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(2);
            then(schema.getProperties()).containsKeys("commonField", "specificField");
            then(schema.getOneOf()).isNotNull();

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "commonField": { "type": "string" },
                        "specificField": { "type": "string" }
                      },
                      "oneOf": [
                        {
                          "type": "object",
                          "properties": {
                            "optionA": { "type": "string" }
                          }
                        },
                        {
                          "type": "object",
                          "properties": {
                            "optionB": { "type": "integer", "format": "int32" }
                          }
                        }
                      ]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should wrap oneOf schema with explicit object type when no properties exist")
        void shouldWrapOneOfSchemaWithExplicitObjectType() throws Exception {
            // given
            var operation = createOperationWithOneOfAndExplicitTypeRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(1);
            then(schema.getProperties()).containsKey("_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_body": {
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "type": { "type": "string", "enum": ["A"] },
                                "valueA": { "type": "string" }
                              }
                            },
                            {
                              "type": "object",
                              "properties": {
                                "type": { "type": "string", "enum": ["B"] },
                                "valueB": { "type": "integer", "format": "int32" }
                              }
                            }
                          ]
                        }
                      },
                      "description": "OneOf request body with explicit object type",
                      "required": ["_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should wrap allOf schema with explicit object type when no properties exist")
        void shouldWrapAllOfSchemaWithExplicitObjectType() throws Exception {
            // given
            var operation = createOperationWithAllOfAndExplicitTypeRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(1);
            then(schema.getProperties()).containsKey("_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_body": {
                          "allOf": [
                            {
                              "type": "object",
                              "properties": {
                                "id": { "type": "string" }
                              }
                            },
                            {
                              "type": "object",
                              "properties": {
                                "name": { "type": "string" }
                              }
                            }
                          ]
                        }
                      },
                      "description": "AllOf request body with explicit object type",
                      "required": ["_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should wrap oneOf schema with parameters using both wrapper keys")
        void shouldWrapOneOfSchemaWithParameters() throws Exception {
            // given
            var operation = createOperationWithParametersAndOneOfRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(2);
            then(schema.getProperties()).containsKeys("_params", "_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_params": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "string",
                              "description": "The user ID"
                            }
                          },
                          "required": ["userId"]
                        },
                        "_body": {
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "type": { "type": "string", "enum": ["A"] },
                                "valueA": { "type": "string" }
                              }
                            },
                            {
                              "type": "object",
                              "properties": {
                                "type": { "type": "string", "enum": ["B"] },
                                "valueB": { "type": "integer", "format": "int32" }
                              }
                            }
                          ]
                        }
                      },
                      "required": ["_params", "_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should wrap non-object schema (array) with parameters without double-wrapping")
        void shouldWrapArraySchemaWithParameters() throws Exception {
            // given
            var operation = createOperationWithParametersAndArrayRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(2);
            then(schema.getProperties()).containsKeys("_params", "_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_params": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "string",
                              "description": "The user ID"
                            }
                          },
                          "required": ["userId"]
                        },
                        "_body": {
                          "type": "array",
                          "items": {
                            "type": "string"
                          }
                        }
                      },
                      "required": ["_params", "_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @DisplayName("Should wrap non-object schema (string) with parameters without double-wrapping")
        void shouldWrapStringSchemaWithParameters() throws Exception {
            // given
            var operation = createOperationWithParametersAndStringRequestBody();

            // when
            var schema = composer.compose(createFullOperation(operation));

            // then
            then(schema).isNotNull();
            then(schema.getType()).isEqualTo("object");
            then(schema.getProperties()).hasSize(2);
            then(schema.getProperties()).containsKeys("_params", "_body");

            var json = jsonRepresentation(schema);
            var expectedJson =
                    """
                    {
                      "type": "object",
                      "properties": {
                        "_params": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "string",
                              "description": "The user ID"
                            }
                          },
                          "required": ["userId"]
                        },
                        "_body": {
                          "type": "string"
                        }
                      },
                      "required": ["_params", "_body"]
                    }
                    """;
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        // Helper methods for composed schema tests

        private Operation createOperationWithOneOfRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ComposedSchema();
            var optionA = new ObjectSchema();
            optionA.addProperty("type", new StringSchema()._enum(List.of("A")));
            optionA.addProperty("valueA", new StringSchema());

            var optionB = new ObjectSchema();
            optionB.addProperty("type", new StringSchema()._enum(List.of("B")));
            optionB.addProperty("valueB", new IntegerSchema());

            schema.oneOf(List.of(optionA, optionB));

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("OneOf request body");
            operation.setRequestBody(requestBody);
            return operation;
        }

        private Operation createOperationWithAllOfRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ComposedSchema();
            var base = new ObjectSchema();
            base.addProperty("id", new StringSchema());

            var extension = new ObjectSchema();
            extension.addProperty("name", new StringSchema());

            schema.allOf(List.of(base, extension));

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("AllOf request body");
            operation.setRequestBody(requestBody);
            return operation;
        }

        private Operation createOperationWithObjectAndOneOfRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ObjectSchema();
            schema.addProperty("commonField", new StringSchema());
            schema.addProperty("specificField", new StringSchema());

            var optionA = new ObjectSchema();
            optionA.addProperty("optionA", new StringSchema());

            var optionB = new ObjectSchema();
            optionB.addProperty("optionB", new IntegerSchema());

            schema.oneOf(List.of(optionA, optionB));

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);
            return operation;
        }

        private Operation createOperationWithParametersAndOneOfRequestBody() {
            var operation = createOperationWithOneOfRequestBody();
            var pathParam = new Parameter()
                    .name("userId")
                    .in("path")
                    .schema(new StringSchema())
                    .description("The user ID")
                    .required(true);
            operation.setParameters(List.of(pathParam));
            return operation;
        }

        private Operation createOperationWithOneOfAndExplicitTypeRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ComposedSchema();
            schema.setType("object"); // Explicitly set type to "object"

            var optionA = new ObjectSchema();
            optionA.addProperty("type", new StringSchema()._enum(List.of("A")));
            optionA.addProperty("valueA", new StringSchema());

            var optionB = new ObjectSchema();
            optionB.addProperty("type", new StringSchema()._enum(List.of("B")));
            optionB.addProperty("valueB", new IntegerSchema());

            schema.oneOf(List.of(optionA, optionB));

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("OneOf request body with explicit object type");
            operation.setRequestBody(requestBody);
            return operation;
        }

        private Operation createOperationWithAllOfAndExplicitTypeRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ComposedSchema();
            schema.setType("object"); // Explicitly set type to "object"

            var base = new ObjectSchema();
            base.addProperty("id", new StringSchema());

            var extension = new ObjectSchema();
            extension.addProperty("name", new StringSchema());

            schema.allOf(List.of(base, extension));

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            requestBody.setDescription("AllOf request body with explicit object type");
            operation.setRequestBody(requestBody);
            return operation;
        }

        private Operation createOperationWithParametersAndArrayRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new ArraySchema();
            schema.setItems(new StringSchema());

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);

            var pathParam = new Parameter()
                    .name("userId")
                    .in("path")
                    .schema(new StringSchema())
                    .description("The user ID")
                    .required(true);
            operation.setParameters(List.of(pathParam));
            return operation;
        }

        private Operation createOperationWithParametersAndStringRequestBody() {
            var operation = new Operation();
            var requestBody = new RequestBody();
            var content = new Content();
            var mediaType = new MediaType();

            var schema = new StringSchema();

            mediaType.setSchema(schema);
            content.addMediaType("application/json", mediaType);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);

            var pathParam = new Parameter()
                    .name("userId")
                    .in("path")
                    .schema(new StringSchema())
                    .description("The user ID")
                    .required(true);
            operation.setParameters(List.of(pathParam));
            return operation;
        }
    }

    @Nested
    @DisplayName("Custom Wrapper Keys Configuration")
    class CustomWrapperKeysTest {

        private final String customParametersKey = "customParams";
        private final String customRequestBodyKey = "customBody";
        private final InputSchemaComposer customComposer = new InputSchemaComposer(
                new OpenApiMcpProperties.Tools.Schema(customParametersKey, customRequestBodyKey));

        @Test
        void shouldUseCustomWrapperKeysInComposition() throws Exception {
            // given
            var operation = createOperationWithParametersAndRequestBody();

            // when
            var result = customComposer.compose(createFullOperation(operation));

            // then
            then(result).isNotNull();

            var json = jsonRepresentation(result);
            var expectedJson = String.format(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "%s": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "string",
                              "description": "The user ID"
                            }
                          },
                          "required": ["userId"]
                        },
                        "%s": {
                          "type": "object",
                          "properties": {
                            "name": {
                              "type": "string"
                            },
                            "email": {
                              "type": "string"
                            }
                          },
                          "description": "User data"
                        }
                      },
                      "required": ["%s", "%s"]
                    }
                    """,
                    customParametersKey, customRequestBodyKey, customParametersKey, customRequestBodyKey);
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldUseCustomWrapperKeysInDecomposition() {
            // given
            var operation = createOperationWithParametersAndRequestBody();
            var arguments = new HashMap<String, Object>();
            arguments.put(customParametersKey, Map.of("userId", "123"));
            arguments.put(
                    customRequestBodyKey,
                    Map.of(
                            "name", "John Doe",
                            "email", "john@example.com"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = customComposer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).isEmpty();
            then(result.parametersByType().header()).isEmpty();
            then(result.parametersByType().cookie()).isEmpty();

            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isInstanceOf(Map.class);
            then((Map<String, Object>) result.requestBody().content())
                    .containsOnly(entry("name", "John Doe"), entry("email", "john@example.com"));
        }

        @Test
        void shouldWarnWhenCustomParametersKeyPresentButOperationHasNoParameters(CapturedOutput output) {
            // given
            var operation = createOperationWithRequestBody(); // Operation with only request body, no parameters
            var arguments = new HashMap<String, Object>();
            arguments.put(customParametersKey, Map.of("userId", "123", "limit", 10)); // Custom parameters key
            arguments.put(customRequestBodyKey, Map.of("name", "John Doe", "email", "john@example.com"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = customComposer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();

            // Verify that warning uses the custom parameters key name
            then(output.getOut())
                    .contains(String.format(
                            "Parameters are not defined in the operation, but '%s' key is present in the input arguments."
                                    + " Skipping passed parameters. Please verify if this is the intended behavior.",
                            customParametersKey));
        }

        @Test
        void shouldWarnWhenCustomRequestBodyKeyPresentButOperationHasNoRequestBody(CapturedOutput output) {
            // given
            var operation = createOperationWithParameters(); // Operation with only parameters, no request body
            var arguments = new HashMap<String, Object>();
            arguments.put(customParametersKey, Map.of("userId", "123", "limit", 10));
            arguments.put(
                    customRequestBodyKey,
                    Map.of("name", "John Doe", "email", "john@example.com")); // Custom request body key
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = customComposer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.parametersByType().query()).containsOnly(entry("limit", 10));
            then(result.requestBody()).isNull();

            // Verify that warning uses the custom request body key name
            then(output.getOut())
                    .contains(String.format(
                            "Request body is not defined in the operation, but '%s' key is present in the input arguments."
                                    + " Skipping passed request body. Please verify if this is the intended behavior.",
                            customRequestBodyKey));
        }

        @Test
        void shouldUseCustomKeysForWrappedNonObjectSchema() throws Exception {
            // given
            var operation = createOperationWithStringRequestBody();

            // when
            var result = customComposer.compose(createFullOperation(operation));

            // then
            then(result).isNotNull();

            var json = jsonRepresentation(result);
            var expectedJson = String.format(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "%s": {
                          "type": "string",
                          "description": "A simple string request body"
                        }
                      },
                      "description": "String request body",
                      "required": ["%s"]
                    }
                    """,
                    customRequestBodyKey, customRequestBodyKey);
            JSONAssert.assertEquals(expectedJson, json, JSONCompareMode.STRICT);
        }

        @Test
        void shouldDecomposeWithCustomKeysForWrappedStringSchema() {
            // given
            var operation = createOperationWithStringRequestBody();
            var arguments = new HashMap<String, Object>();
            arguments.put(customRequestBodyKey, "Hello World"); // Use custom key
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = customComposer.decompose(callToolRequest, operation);

            // then
            then(result).isNotNull();
            then(result.parametersByType()).isEqualTo(DecomposedRequestData.ParametersByType.empty());
            then(result.requestBody()).isNotNull();
            then(result.requestBody().content()).isEqualTo("Hello World");
        }

        @Test
        void shouldHandlePartialCustomKeysCorrectly() {
            // given
            var operation = createOperationWithParametersAndRequestBody();

            // Arguments contain only custom parameters key (no custom request body key)
            var arguments = new HashMap<String, Object>();
            arguments.put(customParametersKey, Map.of("userId", "123"));
            var callToolRequest = new McpSchema.CallToolRequest("test-tool", arguments);

            // when
            var result = customComposer.decompose(callToolRequest, operation);

            // then - Should properly decompose parameters even without custom request body key
            then(result).isNotNull();
            then(result.parametersByType()).isNotNull();
            then(result.parametersByType().path()).containsOnly(entry("userId", "123"));
            then(result.requestBody()).isNull(); // No request body provided
        }
    }
}
