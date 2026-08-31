package com.infobip.openapi.mcp.openapi.filter;

import static org.assertj.core.api.BDDAssertions.then;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NullableTypeNormalizerTest {

    private final NullableTypeNormalizer normalizer = new NullableTypeNormalizer();

    @Test
    void shouldCollapseNullableUnionTypeToScalarInParameterSchema() {
        // Given
        var givenOpenApi = new OpenAPI()
                .specVersion(SpecVersion.V31)
                .path(
                        "/traffic",
                        new PathItem()
                                .get(new Operation()
                                        .parameters(List.of(new Parameter()
                                                .in("query")
                                                .name("metrics")
                                                .schema(nullableTypeSchema("array", "null")
                                                        .items(new StringSchema()))))));

        // When
        var actual = normalizer.filter(givenOpenApi);

        // Then
        var parameterSchema = actual.getPaths()
                .get("/traffic")
                .getGet()
                .getParameters()
                .getFirst()
                .getSchema();
        then(parameterSchema.getTypes()).containsExactly("array");
    }

    @Test
    void shouldCollapseNullableUnionTypeToScalarInRequestBodySchema() {
        // Given
        var bodySchema = new ObjectSchema()
                .addProperty("tags", nullableTypeSchema("array", "null").items(new StringSchema()))
                .addProperty("name", nullableTypeSchema("string", "null"));
        var givenOpenApi = new OpenAPI()
                .specVersion(SpecVersion.V31)
                .path(
                        "/report",
                        new PathItem()
                                .post(new Operation()
                                        .requestBody(new RequestBody()
                                                .content(new Content()
                                                        .addMediaType(
                                                                "application/json",
                                                                new MediaType().schema(bodySchema))))));

        // When
        var actual = normalizer.filter(givenOpenApi);

        // Then
        Map<String, Schema> properties = actual.getPaths()
                .get("/report")
                .getPost()
                .getRequestBody()
                .getContent()
                .get("application/json")
                .getSchema()
                .getProperties();
        then(properties.get("tags").getTypes()).containsExactly("array");
        then(properties.get("name").getTypes()).containsExactly("string");
    }

    @Test
    void shouldCollapseNullableUnionTypeInComponentsSchema() {
        // Given
        var componentSchema = new ObjectSchema()
                .addProperty("value", nullableTypeSchema("string", "null"))
                .addProperty("count", nullableTypeSchema("integer", "null"));
        var givenOpenApi = new OpenAPI()
                .specVersion(SpecVersion.V31)
                .components(new Components().addSchemas("MySchema", componentSchema));

        // When
        var actual = normalizer.filter(givenOpenApi);

        // Then
        Map<String, Schema> properties =
                actual.getComponents().getSchemas().get("MySchema").getProperties();
        then(properties.get("value").getTypes()).containsExactly("string");
        then(properties.get("count").getTypes()).containsExactly("integer");
    }

    @Test
    void shouldNotChangeScalarTypeSchema() {
        // Given
        var givenOpenApi = givenSpecWithScalarTypes();

        // When
        var actual = normalizer.filter(givenOpenApi);

        // Then
        var expectedOpenApi = givenSpecWithScalarTypes();
        then(actual).usingRecursiveComparison().isEqualTo(expectedOpenApi);
    }

    private OpenAPI givenSpecWithScalarTypes() {
        return new OpenAPI()
                .specVersion(SpecVersion.V31)
                .path(
                        "/plain",
                        new PathItem()
                                .get(new Operation()
                                        .parameters(List.of(new Parameter()
                                                .in("query")
                                                .name("items")
                                                .schema(new ArraySchema().items(new StringSchema()))))));
    }

    private Schema<?> nullableTypeSchema(String... types) {
        // OpenAPI 3.1 nullable union type, e.g. {"type": ["array", "null"]}
        Set<String> typeSet = new LinkedHashSet<>(List.of(types));
        return new Schema<>().types(typeSet);
    }
}
