package com.infobip.openapi.mcp.apps;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppStructuredContentTest {

    @Test
    void shouldReturnUnknownWhenOperationHasNoResponses() {
        // Given
        var operation = new Operation();

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(DeclaredSchemaKind.UNKNOWN);
    }

    @Test
    void shouldPreferSchemaOfResponseCode200() {
        // Given
        var operation = new Operation()
                .responses(new ApiResponses()
                        .addApiResponse("2XX", jsonResponse(new ArraySchema()))
                        .addApiResponse("201", jsonResponse(new StringSchema()))
                        .addApiResponse("200", jsonResponse(new ObjectSchema())));

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(DeclaredSchemaKind.OBJECT);
    }

    @Test
    void shouldFallThroughToNextSuccessfulResponseWhenPreferredOneHasNoSchema() {
        // Given
        var operation = new Operation()
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse())
                        .addApiResponse("201", jsonResponse(new ArraySchema())));

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(DeclaredSchemaKind.NON_OBJECT);
    }

    @Test
    void shouldFallThroughToNextSuccessfulResponseWhenPreferredOneHasOnlyNonJsonContent() {
        // Given
        var operation = new Operation()
                .responses(new ApiResponses()
                        .addApiResponse(
                                "200",
                                new ApiResponse()
                                        .content(new Content()
                                                .addMediaType("text/html", new MediaType().example("<html/>"))))
                        .addApiResponse("201", jsonResponse(new ArraySchema())));

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(DeclaredSchemaKind.NON_OBJECT);
    }

    @Test
    void shouldUsePlusJsonSuffixedSchema() {
        // Given
        var operation = new Operation()
                .responses(new ApiResponses()
                        .addApiResponse(
                                "200",
                                new ApiResponse()
                                        .content(new Content()
                                                .addMediaType(
                                                        "application/problem+json",
                                                        new MediaType().schema(new ObjectSchema())))));

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(DeclaredSchemaKind.OBJECT);
    }

    @Test
    void shouldReturnUnknownWhenNoSuccessfulResponseDeclaresJsonSchema() {
        // Given
        var operation = new Operation()
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse())
                        .addApiResponse(
                                "400",
                                new ApiResponse()
                                        .content(new Content()
                                                .addMediaType(
                                                        "application/json",
                                                        new MediaType().schema(new ObjectSchema())))));

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(DeclaredSchemaKind.UNKNOWN);
    }

    public static Stream<Arguments> testDataForSchemaClassification() {
        return Stream.of(
                arguments(new ObjectSchema(), DeclaredSchemaKind.OBJECT),
                arguments(new ArraySchema(), DeclaredSchemaKind.NON_OBJECT),
                arguments(new StringSchema(), DeclaredSchemaKind.NON_OBJECT),
                arguments(new IntegerSchema(), DeclaredSchemaKind.NON_OBJECT),
                // OpenAPI 3.1 union types with the nullable member already removed
                arguments(new Schema<>().types(Set.of("object")), DeclaredSchemaKind.OBJECT),
                arguments(new Schema<>().types(Set.of("array", "integer")), DeclaredSchemaKind.NON_OBJECT),
                arguments(new Schema<>().types(Set.of("object", "string")), DeclaredSchemaKind.UNKNOWN),
                // unresolvable references cannot be trusted
                arguments(new Schema<>().$ref("#/components/schemas/User"), DeclaredSchemaKind.UNKNOWN),
                // type-less schemas classified by their structure
                arguments(new Schema<>().properties(Map.of("id", new StringSchema())), DeclaredSchemaKind.OBJECT),
                arguments(new Schema<>().items(new StringSchema()), DeclaredSchemaKind.NON_OBJECT),
                // combinators
                arguments(
                        new Schema<>()
                                .allOf(List.of(
                                        new ObjectSchema(),
                                        new ObjectSchema().properties(Map.of("name", new StringSchema())))),
                        DeclaredSchemaKind.OBJECT),
                arguments(
                        new Schema<>().oneOf(List.of(new ArraySchema(), new StringSchema())),
                        DeclaredSchemaKind.NON_OBJECT),
                arguments(
                        new Schema<>().oneOf(List.of(new ObjectSchema(), new StringSchema())),
                        DeclaredSchemaKind.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("testDataForSchemaClassification")
    void shouldClassifyDeclaredSchema(Schema<?> schema, DeclaredSchemaKind expectedKind) {
        // Given
        var operation = new Operation().responses(new ApiResponses().addApiResponse("200", jsonResponse(schema)));

        // When / Then
        then(AppStructuredContent.declaredSchemaKind(operation)).isEqualTo(expectedKind);
    }

    public static Stream<Arguments> testDataForComposing() {
        var objectValue = Map.of("id", 1L);
        var arrayValue = List.of("a", "b");
        return Stream.of(
                arguments(DeclaredSchemaKind.OBJECT, objectValue, objectValue),
                arguments(DeclaredSchemaKind.OBJECT, arrayValue, Map.of("result", arrayValue)),
                arguments(DeclaredSchemaKind.NON_OBJECT, arrayValue, Map.of("result", arrayValue)),
                arguments(DeclaredSchemaKind.NON_OBJECT, "scalar", Map.of("result", "scalar")),
                arguments(DeclaredSchemaKind.UNKNOWN, objectValue, objectValue),
                arguments(DeclaredSchemaKind.UNKNOWN, 42L, Map.of("result", 42L)));
    }

    @ParameterizedTest
    @MethodSource("testDataForComposing")
    void shouldComposeStructuredContent(
            DeclaredSchemaKind declaredSchemaKind, Object parsedValue, Object expectedStructuredContent) {
        // When
        var structuredContent = AppStructuredContent.compose(declaredSchemaKind, parsedValue);

        // Then
        then(structuredContent).isEqualTo(expectedStructuredContent);
    }

    @Test
    void shouldComposeNullWhenThereIsNoParsedValue() {
        for (var kind : DeclaredSchemaKind.values()) {
            then(AppStructuredContent.compose(kind, null)).isNull();
        }
    }

    @ParameterizedTest
    @MethodSource("jsonMediaTypeStrings")
    void shouldRecognizeJsonMediaTypes(String mediaTypeString, boolean expected) {
        then(AppStructuredContent.isJsonMediaType(mediaTypeString)).isEqualTo(expected);
    }

    public static Stream<Arguments> jsonMediaTypeStrings() {
        return Stream.of(
                arguments("application/json", true),
                arguments("application/json; charset=utf-8", true),
                arguments("application/problem+json", true),
                arguments("text/html", false),
                arguments("text/html;profile=mcp-app", false),
                arguments("not a media type", false));
    }

    private static ApiResponse jsonResponse(Schema<?> schema) {
        return new ApiResponse()
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }
}
