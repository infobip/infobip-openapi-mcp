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

    // Helper methods to create real OpenAPI specifications with references

    private OpenAPI createOpenAPI30WithSchemaReferences() {
        var openApi = new OpenAPI(SpecVersion.V30);
        openApi.setOpenapi("3.0.1");
        openApi.setInfo(new Info().title("User API").version("1.0.0"));

        // Create components with schemas
        var components = new Components();
        var userSchema = new ObjectSchema();
        userSchema.addProperty("id", new StringSchema());
        userSchema.addProperty("name", new StringSchema());
        userSchema.addProperty("email", new StringSchema());
        components.addSchemas("User", userSchema);
        openApi.setComponents(components);

        // Create path with reference to User schema
        var paths = new Paths();
        var pathItem = new PathItem();
        var getOperation = new Operation();
        getOperation.setOperationId("getUser");

        var response = new ApiResponse();
        var content = new Content();
        var mediaType = new MediaType();

        // Create reference schema
        var refSchema = new Schema<>();
        refSchema.set$ref("#/components/schemas/User");
        mediaType.setSchema(refSchema);

        content.addMediaType("application/json", mediaType);
        response.setContent(content);

        var responses = new ApiResponses();
        responses.addApiResponse("200", response);
        getOperation.setResponses(responses);

        pathItem.setGet(getOperation);
        paths.addPathItem("/users/{id}", pathItem);
        openApi.setPaths(paths);

        return openApi;
    }

    private OpenAPI createOpenAPI31WithComponentReferences() {
        var openApi = new OpenAPI(SpecVersion.V31);
        openApi.setOpenapi("3.1.0");
        openApi.setInfo(new Info().title("User Management API").version("2.0.0"));

        // Create components
        var components = new Components();

        // User schema
        var userSchema = new ObjectSchema();
        userSchema.addProperty("name", new StringSchema());
        userSchema.addProperty("email", new StringSchema());
        components.addSchemas("UserInput", userSchema);

        // Request body component
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();
        var refSchema = new Schema<>();
        refSchema.set$ref("#/components/schemas/UserInput");
        mediaType.setSchema(refSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        components.addRequestBodies("UserRequest", requestBody);

        openApi.setComponents(components);

        // Create path with reference to request body
        var paths = new Paths();
        var pathItem = new PathItem();
        var postOperation = new Operation();
        postOperation.setOperationId("createUser");

        var refRequestBody = new RequestBody();
        refRequestBody.set$ref("#/components/requestBodies/UserRequest");
        postOperation.setRequestBody(refRequestBody);

        var responses = new ApiResponses();
        responses.addApiResponse("201", new ApiResponse().description("Created"));
        postOperation.setResponses(responses);

        pathItem.setPost(postOperation);
        paths.addPathItem("/users", pathItem);
        openApi.setPaths(paths);

        return openApi;
    }

    private OpenAPI createOpenAPIWithNestedReferences() {
        var openApi = new OpenAPI();
        openApi.setSpecVersion(SpecVersion.V31);
        openApi.setInfo(new Info().title("Organization API").version("1.0.0"));

        var components = new Components();

        // User schema
        var userSchema = new ObjectSchema();
        userSchema.addProperty("id", new StringSchema());
        userSchema.addProperty("name", new StringSchema());
        components.addSchemas("User", userSchema);

        // Organization schema referencing User
        var orgSchema = new ObjectSchema();
        orgSchema.addProperty("id", new StringSchema());
        orgSchema.addProperty("name", new StringSchema());
        components.addSchemas("Organization", orgSchema);

        // Response schema with nested references
        var responseSchema = new ObjectSchema();
        var orgRefSchema = new Schema<>();
        orgRefSchema.set$ref("#/components/schemas/Organization");
        responseSchema.addProperty("organization", orgRefSchema);

        var usersArraySchema = new Schema<>();
        usersArraySchema.setType("array");
        var userRefSchema = new Schema<>();
        userRefSchema.set$ref("#/components/schemas/User");
        usersArraySchema.setItems(userRefSchema);
        responseSchema.addProperty("users", usersArraySchema);

        components.addSchemas("OrganizationWithUsers", responseSchema);
        openApi.setComponents(components);

        // Create path
        var paths = new Paths();
        var pathItem = new PathItem();
        var getOperation = new Operation();

        var response = new ApiResponse();
        var content = new Content();
        var mediaType = new MediaType();
        var refResponseSchema = new Schema<>();
        refResponseSchema.set$ref("#/components/schemas/OrganizationWithUsers");
        mediaType.setSchema(refResponseSchema);
        content.addMediaType("application/json", mediaType);
        response.setContent(content);

        var responses = new ApiResponses();
        responses.addApiResponse("200", response);
        getOperation.setResponses(responses);

        pathItem.setGet(getOperation);
        paths.addPathItem("/organizations/{id}/users", pathItem);
        openApi.setPaths(paths);

        return openApi;
    }

    private OpenAPI createOpenAPIWithCombinators() {
        var openApi = new OpenAPI();
        openApi.setSpecVersion(SpecVersion.V31);
        openApi.setInfo(new Info().title("Entity API").version("1.0.0"));

        var components = new Components();

        // Base schemas
        var baseSchema = new ObjectSchema();
        baseSchema.addProperty("id", new StringSchema());
        components.addSchemas("BaseEntity", baseSchema);

        var nameSchema = new ObjectSchema();
        nameSchema.addProperty("name", new StringSchema());
        components.addSchemas("Named", nameSchema);

        // AllOf schema
        var entitySchema = new ObjectSchema();
        var allOfList = List.of(
                new Schema<>().$ref("#/components/schemas/BaseEntity"),
                new Schema<>().$ref("#/components/schemas/Named"));
        entitySchema.setAllOf(allOfList);
        components.addSchemas("Entity", entitySchema);

        openApi.setComponents(components);

        // Create path
        var paths = new Paths();
        var pathItem = new PathItem();
        var postOperation = new Operation();

        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();
        var refSchema = new Schema<>();
        refSchema.set$ref("#/components/schemas/Entity");
        mediaType.setSchema(refSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        postOperation.setRequestBody(requestBody);

        var responses = new ApiResponses();
        responses.addApiResponse("201", new ApiResponse().description("Created"));
        postOperation.setResponses(responses);

        pathItem.setPost(postOperation);
        paths.addPathItem("/entities", pathItem);
        openApi.setPaths(paths);

        return openApi;
    }

    private OpenAPI createOpenAPIWithCircularReferences() {
        var openApi = new OpenAPI();
        openApi.setSpecVersion(SpecVersion.V31);
        openApi.setInfo(new Info().title("Circular Reference API").version("1.0.0"));

        var components = new Components();

        // Create circular reference: Node -> children: Node[]
        var nodeSchema = new ObjectSchema();
        nodeSchema.addProperty("id", new StringSchema());
        nodeSchema.addProperty("name", new StringSchema());

        var childrenArraySchema = new Schema<>();
        childrenArraySchema.setType("array");
        var nodeRefSchema = new Schema<>();
        nodeRefSchema.set$ref("#/components/schemas/Node");
        childrenArraySchema.setItems(nodeRefSchema);
        nodeSchema.addProperty("children", childrenArraySchema);

        components.addSchemas("Node", nodeSchema);
        openApi.setComponents(components);

        return openApi;
    }

    private OpenAPI createOpenAPIWithRequestBodyAndResponseReferences() {
        var openApi = new OpenAPI();
        openApi.setSpecVersion(SpecVersion.V31);
        openApi.setInfo(new Info().title("Request Body Ref API").version("1.0.0"));

        var components = new Components();

        // Schema component
        var userSchema = new ObjectSchema();
        userSchema.addProperty("name", new StringSchema());
        userSchema.addProperty("email", new StringSchema());
        components.addSchemas("User", userSchema);

        // Request body component
        var requestBody = new RequestBody();
        var content = new Content();
        var mediaType = new MediaType();
        var refSchema = new Schema<>();
        refSchema.set$ref("#/components/schemas/User");
        mediaType.setSchema(refSchema);
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        components.addRequestBodies("UserRequest", requestBody);

        // Response component
        var response = new ApiResponse();
        var responseContent = new Content();
        var responseMediaType = new MediaType();
        var responseRefSchema = new Schema<>();
        responseRefSchema.set$ref("#/components/schemas/User");
        responseMediaType.setSchema(responseRefSchema);
        responseContent.addMediaType("application/json", responseMediaType);
        response.setContent(responseContent);
        components.addResponses("UserResponse", response);

        openApi.setComponents(components);

        // Create path with references
        var paths = new Paths();
        var pathItem = new PathItem();
        var postOperation = new Operation();

        var refRequestBody = new RequestBody();
        refRequestBody.set$ref("#/components/requestBodies/UserRequest");
        postOperation.setRequestBody(refRequestBody);

        var responses = new ApiResponses();
        var refResponse = new ApiResponse();
        refResponse.set$ref("#/components/responses/UserResponse");
        responses.addApiResponse("201", refResponse);
        postOperation.setResponses(responses);

        pathItem.setPost(postOperation);
        paths.addPathItem("/users", pathItem);
        openApi.setPaths(paths);

        return openApi;
    }

    private OpenAPI createSimpleOpenAPI30() {
        var openApi = new OpenAPI();
        openApi.setSpecVersion(SpecVersion.V30);
        openApi.setInfo(new Info().title("Simple API").version("1.0.0"));
        return openApi;
    }

    private SwaggerParseResult createSuccessfulParseResult() {
        var parseResult = new SwaggerParseResult();
        parseResult.setOpenAPI(createSimpleOpenAPI30());
        return parseResult;
    }
}
