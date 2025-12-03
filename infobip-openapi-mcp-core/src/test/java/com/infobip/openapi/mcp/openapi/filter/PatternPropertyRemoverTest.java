package com.infobip.openapi.mcp.openapi.filter;

import static org.assertj.core.api.BDDAssertions.then;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternPropertyRemoverTest {

    private final PatternPropertyRemover remover = new PatternPropertyRemover();

    @Test
    void shouldRemovePatternsFromOpenApiSpec() {
        // given
        var givenOpenApi = givenSpecWithPatterns();

        // when
        var actual = remover.filter(givenOpenApi);

        // then
        var expectedOpenApi = givenSpecWithoutPatterns();
        then(actual).usingRecursiveComparison().isEqualTo(expectedOpenApi);
    }

    @Test
    void shouldNotChangeOpenApiSpecThatDoesNotContainPatterns() {
        // given
        var givenOpenApi = givenSpecWithoutPatterns();

        // when
        var actual = remover.filter(givenOpenApi);

        // then
        var expectedOpenApi = givenSpecWithoutPatterns();
        then(actual).usingRecursiveComparison().isEqualTo(expectedOpenApi);
    }

    private OpenAPI givenSpecWithPatterns() {
        return new OpenAPI()
                .components(new Components().addSchemas("mySchema", new StringSchema().pattern("^test&")))
                .path(
                        "/{param}",
                        new PathItem()
                                .post(
                                        new Operation()
                                                .parameters(List.of(
                                                        new Parameter()
                                                                .in("path")
                                                                .required(true)
                                                                .name("param")
                                                                .schema(new StringSchema().pattern("^test$")),
                                                        new Parameter()
                                                                .in("query")
                                                                .required(true)
                                                                .name("query")
                                                                .schema(new StringSchema().pattern("^test$"))))
                                                .requestBody(new RequestBody()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .schema(new StringSchema()
                                                                                        .pattern("^test$")))
                                                                .addMediaType(
                                                                        "application/xml",
                                                                        new MediaType()
                                                                                .schema(new StringSchema()
                                                                                        .pattern("^test$")))))
                                                .responses(
                                                        new ApiResponses()
                                                                .addApiResponse(
                                                                        "200",
                                                                        new ApiResponse()
                                                                                .description("Success")
                                                                                .content(
                                                                                        new Content()
                                                                                                .addMediaType(
                                                                                                        "application/json",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()
                                                                                                                                .pattern(
                                                                                                                                        "^test$")))
                                                                                                .addMediaType(
                                                                                                        "application/xml",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()
                                                                                                                                .pattern(
                                                                                                                                        "^test$")))))
                                                                .addApiResponse(
                                                                        "400",
                                                                        new ApiResponse()
                                                                                .description("Bad Request")
                                                                                .content(
                                                                                        new Content()
                                                                                                .addMediaType(
                                                                                                        "application/json",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()
                                                                                                                                .pattern(
                                                                                                                                        "^test$")))
                                                                                                .addMediaType(
                                                                                                        "application/xml",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()
                                                                                                                                .pattern(
                                                                                                                                        "^test$"))))))));
    }

    private OpenAPI givenSpecWithoutPatterns() {
        return new OpenAPI()
                .components(new Components().addSchemas("mySchema", new StringSchema()))
                .path(
                        "/{param}",
                        new PathItem()
                                .post(
                                        new Operation()
                                                .parameters(List.of(
                                                        new Parameter()
                                                                .in("path")
                                                                .required(true)
                                                                .name("param")
                                                                .schema(new StringSchema()),
                                                        new Parameter()
                                                                .in("query")
                                                                .required(true)
                                                                .name("query")
                                                                .schema(new StringSchema())))
                                                .requestBody(new RequestBody()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType().schema(new StringSchema()))
                                                                .addMediaType(
                                                                        "application/xml",
                                                                        new MediaType().schema(new StringSchema()))))
                                                .responses(
                                                        new ApiResponses()
                                                                .addApiResponse(
                                                                        "200",
                                                                        new ApiResponse()
                                                                                .description("Success")
                                                                                .content(
                                                                                        new Content()
                                                                                                .addMediaType(
                                                                                                        "application/json",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()))
                                                                                                .addMediaType(
                                                                                                        "application/xml",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()))))
                                                                .addApiResponse(
                                                                        "400",
                                                                        new ApiResponse()
                                                                                .description("Bad Request")
                                                                                .content(
                                                                                        new Content()
                                                                                                .addMediaType(
                                                                                                        "application/json",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema()))
                                                                                                .addMediaType(
                                                                                                        "application/xml",
                                                                                                        new MediaType()
                                                                                                                .schema(
                                                                                                                        new StringSchema())))))));
    }
}
