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
import java.util.List;
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "user-123"))));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "user-456"))));
    }

    @Test
    void shouldExtractParameterSchemaExample() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(
                new Parameter().name("userId").in("query").schema(new StringSchema().example("user-789")));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "user-789"))));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "from-examples-map"))));
    }

    @Test
    void shouldPreferParameterExampleOverSchemaExample() {
        // Given
        var operation = new Operation();
        operation.addParametersItem(new Parameter()
                .name("userId")
                .in("query")
                .example("from-parameter")
                .schema(new StringSchema().example("from-schema")));

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "from-parameter"))));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "user-123"))));
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
        var expectedParams = new LinkedHashMap<String, Object>();
        expectedParams.put("q", "queryVal");
        expectedParams.put("id", "pathVal");
        expectedParams.put("h", "headerVal");
        expectedParams.put("c", "cookieVal");
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, expectedParams)));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, bodyExample)));
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
        then(result)
                .hasSize(2)
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new ComposedExample("Standard SMS", null, firstValue),
                        new ComposedExample("Flash SMS", "A flash message", secondValue)));
    }

    @Test
    void shouldExtractMediaTypeSchemaExample() {
        // Given
        var schemaExample = Map.of("to", "41793026727");
        var bodySchema = new ObjectSchema();
        bodySchema.setExample(schemaExample);
        var mediaType = new MediaType().schema(bodySchema);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, schemaExample)));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample("e1", null, examplesMapValue)));
    }

    @Test
    void shouldPreferMediaTypeExampleOverSchemaExample() {
        // Given
        var schemaWithExample = new ObjectSchema();
        schemaWithExample.setExample(Map.of("source", "schema"));
        var directExample = Map.of("source", "mediaType");
        var mediaType = new MediaType().schema(schemaWithExample).example(directExample);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, directExample)));
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
        var expectedParams = new LinkedHashMap<String, Object>();
        expectedParams.put("userId", "user-123");
        expectedParams.put("limit", 10);
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, expectedParams)));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, bodyExample)));
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
        var expectedValue = new LinkedHashMap<String, Object>();
        expectedValue.put("_params", Map.of("userId", "user-123"));
        expectedValue.put("_body", bodyExample);
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, expectedValue)));
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
        var firstExpected = new LinkedHashMap<String, Object>();
        firstExpected.put("_params", Map.of("userId", "user-123"));
        firstExpected.put("_body", firstBody);
        var secondExpected = new LinkedHashMap<String, Object>();
        secondExpected.put("_params", Map.of("userId", "user-123"));
        secondExpected.put("_body", secondBody);
        then(result)
                .hasSize(2)
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new ComposedExample("Standard SMS", null, firstExpected),
                        new ComposedExample("Flash SMS", null, secondExpected)));
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
        var expectedValue = new LinkedHashMap<String, Object>();
        expectedValue.put("parameters", Map.of("id", "123"));
        expectedValue.put("body", bodyExample);
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, expectedValue)));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "user-resolved"))));
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
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample(null, null, Map.of("userId", "good-value"))));
    }

    @Test
    void shouldUseMapKeyAsTitleWhenExampleSummaryIsAbsent() {
        // Given
        var firstValue = Map.of("to", "41793026727");
        var secondValue = Map.of("to", "41793026728");
        var examples = new LinkedHashMap<String, Example>();
        examples.put("sms-example", new Example().value(firstValue));
        examples.put("flash-example", new Example().value(secondValue));
        var mediaType = new MediaType().examples(examples);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then — map keys used as titles when summary is absent
        then(result)
                .hasSize(2)
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new ComposedExample("sms-example", null, firstValue),
                        new ComposedExample("flash-example", null, secondValue)));
    }

    @Test
    void shouldUseMapKeyAsTitleWhenExampleSummaryIsBlank() {
        // Given
        var value = Map.of("to", "41793026727");
        var examples = Map.of("my-example", new Example().summary("  ").value(value));
        var mediaType = new MediaType().examples(examples);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then — blank summary treated the same as absent; key used instead
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample("my-example", null, value)));
    }

    @Test
    void shouldPreferSummaryOverMapKeyWhenSummaryIsPresent() {
        // Given
        var value = Map.of("to", "41793026727");
        var examples =
                Map.of("internal-key", new Example().summary("Friendly Title").value(value));
        var mediaType = new MediaType().examples(examples);
        var operation = operationWithBody(mediaType);

        // When
        var result = composer.composeExamples(fullOperation(operation));

        // Then — explicit summary takes precedence over key
        then(result)
                .hasSize(1)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ComposedExample("Friendly Title", null, value)));
    }

    // -- Helpers --

    private static FullOperation fullOperation(Operation operation) {
        return new FullOperation("/test", PathItem.HttpMethod.GET, operation, new OpenAPI());
    }

    private static Operation operationWithBody(MediaType mediaType) {
        return new Operation()
                .requestBody(new RequestBody().content(new Content().addMediaType("application/json", mediaType)));
    }
}
