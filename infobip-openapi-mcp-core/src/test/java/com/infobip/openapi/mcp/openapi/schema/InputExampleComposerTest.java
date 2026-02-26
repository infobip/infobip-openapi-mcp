package com.infobip.openapi.mcp.openapi.schema;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InputExampleComposerTest {

    private final InputExampleComposer composer =
            new InputExampleComposer(new OpenApiMcpProperties.Tools.Schema(null, null));

    // -- No examples --

    @Test
    void shouldReturnEmptyListWhenNoExamplesExist() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").schema(new StringSchema()));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenOperationHasNoParametersAndNoBody() {
        // Given
        var operation = new Operation();

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).isEmpty();
    }

    // -- Parameter examples --

    @Test
    void shouldExtractParameterExample() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").example("user-123"));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).title()).isNull();
        then(result.get(0).description()).isNull();
        then(result.get(0).value()).isEqualTo(Map.of("userId", "user-123"));
    }

    @Test
    void shouldExtractParameterExamplesMap() {
        // Given
        var example = new Example().value("user-456");
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").examples(Map.of("example1", example)));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "user-456"));
    }

    @Test
    void shouldExtractParameterSchemaExample() {
        // Given
        var schema = new StringSchema();
        schema.setExample("user-789");
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").schema(schema));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "user-789"));
    }

    @Test
    void shouldPreferParameterExamplesOverParameterExample() {
        // Given
        var example = new Example().value("from-examples-map");
        var operation = new Operation();
        operation.addParametersItem(new Parameter()
                .name("userId")
                .in("query")
                .example("from-example")
                .examples(Map.of("e1", example)));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "from-examples-map"));
    }

    @Test
    void shouldPreferParameterExampleOverSchemaExample() {
        // Given
        var schema = new StringSchema();
        schema.setExample("from-schema");
        var operation = new Operation();
        operation.addParametersItem(new Parameter()
                .name("userId")
                .in("query")
                .example("from-parameter")
                .schema(schema));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "from-parameter"));
    }

    @Test
    void shouldOnlyIncludeParametersWithExamples() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").example("user-123"));
        operation.addParametersItem(new Parameter().name("status").in("query").schema(new StringSchema()));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "user-123"));
    }

    @Test
    void shouldSkipUnsupportedParameterTypes() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(
                new Parameter().name("unsupported").in("matrix").example("val"));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldExtractExamplesFromAllSupportedParameterTypes() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("q").in("query").example("queryVal"));
        operation.addParametersItem(new Parameter().name("id").in("path").example("pathVal"));
        operation.addParametersItem(new Parameter().name("h").in("header").example("headerVal"));
        operation.addParametersItem(new Parameter().name("c").in("cookie").example("cookieVal"));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        @SuppressWarnings("unchecked")
        var resultMap = (Map<String, Object>) result.get(0).value();
        then(resultMap)
                .containsEntry("q", "queryVal")
                .containsEntry("id", "pathVal")
                .containsEntry("h", "headerVal")
                .containsEntry("c", "cookieVal");
    }

    // -- Body examples --

    @Test
    void shouldExtractMediaTypeExample() {
        // Given
        var bodyExample = Map.of("to", "41793026727", "text", "Hello");
        var mediaType = new MediaType().example(bodyExample);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).title()).isNull();
        then(result.get(0).value()).isEqualTo(bodyExample);
    }

    @Test
    void shouldExtractAllEntriesFromMediaTypeExamplesMap() {
        // Given
        var firstValue = Map.of("to", "41793026727");
        var secondValue = Map.of("to", "41793026728");
        var examples = new LinkedHashMap<String, Example>();
        examples.put("sms", new Example().summary("Standard SMS").value(firstValue));
        examples.put(
                "flash",
                new Example()
                        .summary("Flash SMS")
                        .description("A flash message")
                        .value(secondValue));
        var mediaType = new MediaType().examples(examples);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then — both entries preserved in insertion order
        then(result).hasSize(2);
        then(result.get(0).title()).isEqualTo("Standard SMS");
        then(result.get(0).description()).isNull();
        then(result.get(0).value()).isEqualTo(firstValue);
        then(result.get(1).title()).isEqualTo("Flash SMS");
        then(result.get(1).description()).isEqualTo("A flash message");
        then(result.get(1).value()).isEqualTo(secondValue);
    }

    @Test
    void shouldExtractMediaTypeSchemaExample() {
        // Given
        var schemaExample = Map.of("to", "41793026727");
        var schema = new ObjectSchema();
        schema.setExample(schemaExample);
        var mediaType = new MediaType().schema(schema);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(schemaExample);
    }

    @Test
    void shouldPreferMediaTypeExamplesOverMediaTypeExample() {
        // Given
        var examplesMapValue = Map.of("source", "examples-map");
        var example = new Example().value(examplesMapValue);
        var mediaType = new MediaType().example(Map.of("source", "example")).examples(Map.of("e1", example));
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(examplesMapValue);
    }

    @Test
    void shouldPreferMediaTypeExampleOverSchemaExample() {
        // Given
        var schema = new ObjectSchema();
        schema.setExample(Map.of("source", "schema"));
        var directExample = Map.of("source", "mediaType");
        var mediaType = new MediaType().schema(schema).example(directExample);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(directExample);
    }

    @Test
    void shouldReturnEmptyListWhenBodyHasNoJsonMediaType() {
        // Given
        var content = new Content();
        content.addMediaType("application/xml", new MediaType().example("<xml/>"));
        var requestBody = new RequestBody().content(content);
        var operation = new Operation().requestBody(requestBody);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).isEmpty();
    }

    // -- Combination rules --

    @Test
    void shouldReturnFlatMapWhenOnlyParamsHaveExamples() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").example("user-123"));
        operation.addParametersItem(new Parameter().name("limit").in("query").example(10));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        var expected = new LinkedHashMap<String, Object>();
        expected.put("userId", "user-123");
        expected.put("limit", 10);
        then(result.get(0).value()).isEqualTo(expected);
    }

    @Test
    void shouldReturnBodyDirectlyWhenOnlyBodyHasExamples() {
        // Given
        var bodyExample = Map.of("to", "41793026727", "text", "Hello");
        var mediaType = new MediaType().example(bodyExample);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(bodyExample);
    }

    @Test
    void shouldWrapUnderKeysWhenBothParamsAndBodyHaveExamples() {
        // Given
        var bodyExample = Map.of("to", "41793026727", "text", "Hello");
        var mediaType = new MediaType().example(bodyExample);

        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").example("user-123"));
        operation.setRequestBody(new RequestBody().content(new Content().addMediaType("application/json", mediaType)));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        @SuppressWarnings("unchecked")
        var resultMap = (Map<String, Object>) result.get(0).value();
        then(resultMap).containsKey("_params").containsKey("_body");
        then(resultMap.get("_params")).isEqualTo(Map.of("userId", "user-123"));
        then(resultMap.get("_body")).isEqualTo(bodyExample);
    }

    @Test
    void shouldProduceOneWrappedEntryPerBodyExampleWhenBothParamsAndMultipleBodyExamples() {
        // Given
        var firstBody = Map.of("to", "41793026727", "text", "Hello");
        var secondBody = Map.of("to", "41793026728", "text", "Flash");
        var examples = new LinkedHashMap<String, Example>();
        examples.put("sms", new Example().summary("Standard SMS").value(firstBody));
        examples.put("flash", new Example().summary("Flash SMS").value(secondBody));

        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").example("user-123"));
        operation.setRequestBody(new RequestBody()
                .content(new Content().addMediaType("application/json", new MediaType().examples(examples))));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then — one entry per body example, each merging the same params
        then(result).hasSize(2);

        @SuppressWarnings("unchecked")
        var firstMap = (Map<String, Object>) result.get(0).value();
        then(result.get(0).title()).isEqualTo("Standard SMS");
        then(firstMap.get("_params")).isEqualTo(Map.of("userId", "user-123"));
        then(firstMap.get("_body")).isEqualTo(firstBody);

        @SuppressWarnings("unchecked")
        var secondMap = (Map<String, Object>) result.get(1).value();
        then(result.get(1).title()).isEqualTo("Flash SMS");
        then(secondMap.get("_params")).isEqualTo(Map.of("userId", "user-123"));
        then(secondMap.get("_body")).isEqualTo(secondBody);
    }

    // -- Custom keys --

    @Test
    void shouldUseCustomKeysWhenBothParamsAndBodyHaveExamples() {
        // Given
        var customComposer = new InputExampleComposer(new OpenApiMcpProperties.Tools.Schema("parameters", "body"));

        var bodyExample = Map.of("to", "41793026727");
        var mediaType = new MediaType().example(bodyExample);

        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("id").in("path").example("123"));
        operation.setRequestBody(new RequestBody().content(new Content().addMediaType("application/json", mediaType)));

        // When
        var result = customComposer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        @SuppressWarnings("unchecked")
        var resultMap = (Map<String, Object>) result.get(0).value();
        then(resultMap).containsKey("parameters").containsKey("body");
        then(resultMap.get("parameters")).isEqualTo(Map.of("id", "123"));
        then(resultMap.get("body")).isEqualTo(bodyExample);
    }

    // -- Example Objects with value --

    @Test
    void shouldExtractValueFromExampleObject() {
        // Given
        var example = new Example()
                .summary("A user ID example")
                .description("Shows a typical user ID")
                .value("user-resolved");
        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").examples(Map.of("ex1", example)));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "user-resolved"));
    }

    @Test
    void shouldSkipExampleObjectWithNullValue() {
        // Given
        var exampleWithoutValue = new Example().summary("No value");
        var exampleWithValue = new Example().value("good-value");
        var examples = new LinkedHashMap<String, Example>();
        examples.put("noVal", exampleWithoutValue);
        examples.put("hasVal", exampleWithValue);

        var operation = new Operation();
        operation.addParametersItem(new Parameter().name("userId").in("query").examples(examples));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result).hasSize(1);
        then(result.get(0).value()).isEqualTo(Map.of("userId", "good-value"));
    }

    // -- Helpers --

    private static FullOperation fullOperation(Operation operation) {
        return new FullOperation("/test", PathItem.HttpMethod.GET, operation, new OpenAPI());
    }

    private static Operation operationWithBody(MediaType mediaType) {
        var content = new Content();
        content.addMediaType("application/json", mediaType);
        var requestBody = new RequestBody().content(content);
        return new Operation().requestBody(requestBody);
    }
}
