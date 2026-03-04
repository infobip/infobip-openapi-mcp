package com.infobip.openapi.mcp.openapi;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.infobip.openapi.mcp.openapi.exception.InvalidOpenApiException;
import com.infobip.openapi.mcp.util.OpenApiMapperFactory;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OpenApiResolverTest {

    @Mock
    private OpenAPIV3Parser parser;

    private final OpenApiMapperFactory mapperFactory = new OpenApiMapperFactory();

    private OpenApiResolver resolverWithMockedParser;

    @BeforeEach
    void setUp() {
        resolverWithMockedParser = new OpenApiResolver(parser, mapperFactory);
    }

    @Test
    void shouldResolveOpenAPI30WithSchemaReferences() {
        // given
        var openApiWithRefs = createOpenAPI30WithSchemaReferences();
        var realParser = new OpenAPIV3Parser();

        // Use real parser for actual resolution
        var realResolver = new OpenApiResolver(realParser, mapperFactory);

        // when
        var result = realResolver.resolve(openApiWithRefs);

        // then
        then(result).isNotNull();
        then(result.getSpecVersion()).isEqualTo(SpecVersion.V30);

        // Verify that references are resolved
        var getUserOperation = result.getPaths().get("/users/{id}").getGet();
        Schema<?> responseSchema = getUserOperation
                .getResponses()
                .get("200")
                .getContent()
                .get("application/json")
                .getSchema();

        // The schema should be resolved (no longer a reference)
        then(responseSchema.getType()).isEqualTo("object");
        then(responseSchema.getProperties()).containsOnlyKeys("id", "name", "email");
    }

    @Test
    void shouldResolveOpenAPI31WithComponentReferences() {
        // given
        var openApiWithRefs = createOpenAPI31WithComponentReferences();
        var realParser = new OpenAPIV3Parser();
        var realResolver = new OpenApiResolver(realParser, mapperFactory);

        // when
        var result = realResolver.resolve(openApiWithRefs);

        // then
        then(result).isNotNull();
        then(result.getSpecVersion()).isEqualTo(SpecVersion.V31);

        // Verify that parameter references are resolved
        var createUserOperation = result.getPaths().get("/users").getPost();
        Schema<?> requestBodySchema = createUserOperation
                .getRequestBody()
                .getContent()
                .get("application/json")
                .getSchema();

        // The schema should be resolved
        then(requestBodySchema.getTypes()).containsExactly("object");
        then(requestBodySchema.getProperties()).containsOnlyKeys("name", "email");
    }

    @Test
    void shouldResolveNestedSchemaReferences() {
        // given
        var openApiWithNestedRefs = createOpenAPIWithNestedReferences();
        var realParser = new OpenAPIV3Parser();
        var realResolver = new OpenApiResolver(realParser, mapperFactory);

        // when
        var result = realResolver.resolve(openApiWithNestedRefs);

        // then
        then(result).isNotNull();

        // Verify that nested references are resolved
        var operation = result.getPaths().get("/organizations/{id}/users").getGet();
        Schema<?> responseSchema = operation
                .getResponses()
                .get("200")
                .getContent()
                .get("application/json")
                .getSchema();

        then(responseSchema.getType()).isEqualTo("object");
        then(responseSchema.getProperties()).containsOnlyKeys("organization", "users");

        // Verify nested user schema is also resolved
        var usersArraySchema = responseSchema.getProperties().get("users");
        then(usersArraySchema.getType()).isEqualTo("array");
    }

    @Test
    void shouldResolveCombinatorSchemas() {
        // given
        var openApiWithCombinators = createOpenAPIWithCombinators();
        var realParser = new OpenAPIV3Parser();
        var realResolver = new OpenApiResolver(realParser, mapperFactory);

        // when
        var result = realResolver.resolve(openApiWithCombinators);

        // then
        then(result).isNotNull();

        // Verify that combinator schemas are resolved
        var operation = result.getPaths().get("/entities").getPost();
        Schema<?> requestBodySchema =
                operation.getRequestBody().getContent().get("application/json").getSchema();

        then(requestBodySchema).isNotNull();

        // If allOf is flattened, verify the resulting object schema has both properties
        then(requestBodySchema.getAllOf()).isNull();
        then(requestBodySchema.getType()).isEqualTo("object");
        then(requestBodySchema.getProperties()).containsKeys("id", "name");

        // Verify the components still exist but are resolved
        then(result.getComponents().getSchemas()).containsKeys("BaseEntity", "Named", "Entity");

        Schema<?> baseEntitySchema = result.getComponents().getSchemas().get("BaseEntity");
        then(baseEntitySchema.getType()).isEqualTo("object");
        then(baseEntitySchema.getProperties()).containsOnlyKeys("id");

        Schema<?> namedSchema = result.getComponents().getSchemas().get("Named");
        then(namedSchema.getType()).isEqualTo("object");
        then(namedSchema.getProperties()).containsOnlyKeys("name");
    }

    @Test
    void shouldConfigureParseOptionsCorrectly() {
        // given
        var inputOpenAPI = createSimpleOpenAPI30();

        given(parser.readContents(anyString(), isNull(), any(ParseOptions.class)))
                .willReturn(createSuccessfulParseResult());

        // when
        resolverWithMockedParser.resolve(inputOpenAPI);

        // then
        var parseOptionsCaptor = ArgumentCaptor.forClass(ParseOptions.class);
        verify(parser).readContents(anyString(), isNull(), parseOptionsCaptor.capture());

        var capturedOptions = parseOptionsCaptor.getValue();
        then(capturedOptions.isResolve()).isTrue();
        then(capturedOptions.isResolveFully()).isTrue();
        then(capturedOptions.isResolveRequestBody()).isTrue();
        then(capturedOptions.isResolveResponses()).isTrue();
        then(capturedOptions.isResolveCombinators()).isTrue();
    }

    @Test
    void shouldThrowInvalidOpenAPIExceptionWhenResolutionFails(CapturedOutput output) {
        // given
        var inputOpenAPI = createSimpleOpenAPI30();
        var parseResult = new SwaggerParseResult();
        parseResult.setOpenAPI(null); // Simulate resolution failure
        var errors = List.of("Reference resolution failed", "Invalid schema");
        parseResult.setMessages(errors);

        given(parser.readContents(anyString(), isNull(), any(ParseOptions.class)))
                .willReturn(parseResult);

        // when & then
        thenThrownBy(() -> resolverWithMockedParser.resolve(inputOpenAPI))
                .isInstanceOf(InvalidOpenApiException.class)
                .hasMessageContaining("Invalid OpenAPI spec: errors occurred while resolving references.")
                .hasFieldOrPropertyWithValue("messages", errors);

        // Verify error logging for parse result messages
        then(output.getOut())
                .contains(
                        "Resolving OpenAPI spec resulted in the following errors: Reference resolution failed; Invalid schema");
    }

    @Test
    void shouldHandleCircularReferences() {
        // given
        var openApiWithCircularRefs = createOpenAPIWithCircularReferences();
        var realParser = new OpenAPIV3Parser();
        var realResolver = new OpenApiResolver(realParser, mapperFactory);

        // when
        var result = realResolver.resolve(openApiWithCircularRefs);

        // then
        then(result).isNotNull();
        // The resolver should handle circular references gracefully
        then(result.getComponents().getSchemas()).isNotEmpty();
        then(result.getComponents().getSchemas()).containsOnlyKeys("Node");
    }

    @Test
    void shouldResolveRequestBodyAndResponseReferences() {
        // given
        var openApiWithBodyRefs = createOpenAPIWithRequestBodyAndResponseReferences();
        var realParser = new OpenAPIV3Parser();
        var realResolver = new OpenApiResolver(realParser, mapperFactory);

        // when
        var result = realResolver.resolve(openApiWithBodyRefs);

        // then
        then(result).isNotNull();

        var operation = result.getPaths().get("/users").getPost();

        // Verify request body is resolved
        var requestBody = operation.getRequestBody();
        Schema<?> requestBodySchema =
                requestBody.getContent().get("application/json").getSchema();
        then(requestBodySchema).isNotNull();
        then(requestBodySchema.getType()).isEqualTo("object");
        then(requestBodySchema.getProperties()).containsOnlyKeys("name", "email");

        // Verify response is resolved
        var response = operation.getResponses().get("201");
        Schema<?> responseSchema = response.getContent().get("application/json").getSchema();
        then(responseSchema).isNotNull();
        then(responseSchema.getType()).isEqualTo("object");
        then(responseSchema.getProperties()).containsOnlyKeys("name", "email");
    }

    @Test
    void shouldPreserveAllExamplesMapEntriesAfterRoundTrip() {
        // Given
        var spec = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "T", "version": "1.0" },
                  "paths": {
                    "/test": {
                      "post": {
                        "operationId": "test",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "examples": {
                                "first":  { "summary": "First",  "value": { "a": 1 } },
                                "second": { "summary": "Second", "value": { "b": 2 } }
                              }
                            }
                          }
                        },
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  }
                }
                """;
        var realParser = new OpenAPIV3Parser();
        var realResolver = new OpenApiResolver(realParser, mapperFactory);
        var initial = realParser.readContents(spec, null, new ParseOptions()).getOpenAPI();

        // When
        var resolved = realResolver.resolve(initial);

        // Then
        var mediaType = resolved.getPaths()
                .get("/test")
                .getPost()
                .getRequestBody()
                .getContent()
                .get("application/json");
        then(mediaType.getExamples()).isNotNull().hasSize(2);
        then(mediaType.getExamples()).containsKeys("first", "second");
        then(mediaType.getExamples().get("first").getSummary()).isEqualTo("First");
        then(mediaType.getExamples().get("second").getSummary()).isEqualTo("Second");
    }

    // Helper methods to create real OpenAPI specifications with references

    private OpenAPI createOpenAPI30WithSchemaReferences() {
        var userSchema = new ObjectSchema()
                .addProperty("id", new StringSchema())
                .addProperty("name", new StringSchema())
                .addProperty("email", new StringSchema());

        var getOperation = new Operation()
                .operationId("getUser")
                .responses(new ApiResponses()
                        .addApiResponse(
                                "200",
                                new ApiResponse()
                                        .content(new Content()
                                                .addMediaType(
                                                        "application/json",
                                                        new MediaType()
                                                                .schema(new Schema<>()
                                                                        .$ref("#/components/schemas/User"))))));

        return new OpenAPI(SpecVersion.V30)
                .openapi("3.0.1")
                .info(new Info().title("User API").version("1.0.0"))
                .components(new Components().addSchemas("User", userSchema))
                .paths(new Paths().addPathItem("/users/{id}", new PathItem().get(getOperation)));
    }

    private OpenAPI createOpenAPI31WithComponentReferences() {
        var userSchema =
                new ObjectSchema().addProperty("name", new StringSchema()).addProperty("email", new StringSchema());

        var requestBodyComponent = new RequestBody()
                .content(new Content()
                        .addMediaType(
                                "application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/UserInput"))));

        var postOperation = new Operation()
                .operationId("createUser")
                .requestBody(new RequestBody().$ref("#/components/requestBodies/UserRequest"))
                .responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("Created")));

        return new OpenAPI(SpecVersion.V31)
                .openapi("3.1.0")
                .info(new Info().title("User Management API").version("2.0.0"))
                .components(new Components()
                        .addSchemas("UserInput", userSchema)
                        .addRequestBodies("UserRequest", requestBodyComponent))
                .paths(new Paths().addPathItem("/users", new PathItem().post(postOperation)));
    }

    private OpenAPI createOpenAPIWithNestedReferences() {
        var userSchema =
                new ObjectSchema().addProperty("id", new StringSchema()).addProperty("name", new StringSchema());

        var orgSchema =
                new ObjectSchema().addProperty("id", new StringSchema()).addProperty("name", new StringSchema());

        var usersArraySchema = new ArraySchema().items(new Schema<>().$ref("#/components/schemas/User"));

        var responseSchema = new ObjectSchema()
                .addProperty("organization", new Schema<>().$ref("#/components/schemas/Organization"))
                .addProperty("users", usersArraySchema);

        var getOperation = new Operation()
                .responses(
                        new ApiResponses()
                                .addApiResponse(
                                        "200",
                                        new ApiResponse()
                                                .content(
                                                        new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .schema(
                                                                                        new Schema<>()
                                                                                                .$ref(
                                                                                                        "#/components/schemas/OrganizationWithUsers"))))));

        return new OpenAPI()
                .specVersion(SpecVersion.V31)
                .info(new Info().title("Organization API").version("1.0.0"))
                .components(new Components()
                        .addSchemas("User", userSchema)
                        .addSchemas("Organization", orgSchema)
                        .addSchemas("OrganizationWithUsers", responseSchema))
                .paths(new Paths().addPathItem("/organizations/{id}/users", new PathItem().get(getOperation)));
    }

    private OpenAPI createOpenAPIWithCombinators() {
        var baseSchema = new ObjectSchema().addProperty("id", new StringSchema());
        var nameSchema = new ObjectSchema().addProperty("name", new StringSchema());
        var entitySchema = new ObjectSchema()
                .allOf(List.of(
                        new Schema<>().$ref("#/components/schemas/BaseEntity"),
                        new Schema<>().$ref("#/components/schemas/Named")));

        var postOperation = new Operation()
                .requestBody(new RequestBody()
                        .content(new Content()
                                .addMediaType(
                                        "application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/Entity")))))
                .responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("Created")));

        return new OpenAPI()
                .specVersion(SpecVersion.V31)
                .info(new Info().title("Entity API").version("1.0.0"))
                .components(new Components()
                        .addSchemas("BaseEntity", baseSchema)
                        .addSchemas("Named", nameSchema)
                        .addSchemas("Entity", entitySchema))
                .paths(new Paths().addPathItem("/entities", new PathItem().post(postOperation)));
    }

    private OpenAPI createOpenAPIWithCircularReferences() {
        var childrenArraySchema = new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Node"));
        var nodeSchema = new ObjectSchema()
                .addProperty("id", new StringSchema())
                .addProperty("name", new StringSchema())
                .addProperty("children", childrenArraySchema);

        return new OpenAPI()
                .specVersion(SpecVersion.V31)
                .info(new Info().title("Circular Reference API").version("1.0.0"))
                .components(new Components().addSchemas("Node", nodeSchema));
    }

    private OpenAPI createOpenAPIWithRequestBodyAndResponseReferences() {
        var userSchema =
                new ObjectSchema().addProperty("name", new StringSchema()).addProperty("email", new StringSchema());

        var requestBodyComponent = new RequestBody()
                .content(new Content()
                        .addMediaType(
                                "application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/User"))));

        var responseComponent = new ApiResponse()
                .content(new Content()
                        .addMediaType(
                                "application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/User"))));

        var postOperation = new Operation()
                .requestBody(new RequestBody().$ref("#/components/requestBodies/UserRequest"))
                .responses(new ApiResponses()
                        .addApiResponse("201", new ApiResponse().$ref("#/components/responses/UserResponse")));

        return new OpenAPI()
                .specVersion(SpecVersion.V31)
                .info(new Info().title("Request Body Ref API").version("1.0.0"))
                .components(new Components()
                        .addSchemas("User", userSchema)
                        .addRequestBodies("UserRequest", requestBodyComponent)
                        .addResponses("UserResponse", responseComponent))
                .paths(new Paths().addPathItem("/users", new PathItem().post(postOperation)));
    }

    private OpenAPI createSimpleOpenAPI30() {
        return new OpenAPI()
                .specVersion(SpecVersion.V30)
                .info(new Info().title("Simple API").version("1.0.0"));
    }

    private SwaggerParseResult createSuccessfulParseResult() {
        var parseResult = new SwaggerParseResult();
        parseResult.setOpenAPI(createSimpleOpenAPI30());
        return parseResult;
    }
}
