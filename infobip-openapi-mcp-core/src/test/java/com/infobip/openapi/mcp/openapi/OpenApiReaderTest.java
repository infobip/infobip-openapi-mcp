package com.infobip.openapi.mcp.openapi;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.openapi.exception.InvalidOpenApiException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OpenApiReaderTest {

    @Nested
    class MockedParserTests {

        @Mock
        private OpenAPIV3Parser parser;

        @Captor
        private ArgumentCaptor<ParseOptions> parseOptionsCaptor;

        private OpenApiReader openApiReader;

        private final URI testUri = URI.create("https://example.com/api/openapi.yaml");

        @BeforeEach
        void setUp() {
            openApiReader = new OpenApiReader(parser);
        }

        @Test
        void shouldSuccessfullyParseValidOpenAPIDocument() {
            // Given
            var expectedOpenAPI = new OpenAPI()
                    .info(new Info().title("Test API").version("1.0.0").description("Test API Description"));

            var parseResult = new SwaggerParseResult();
            parseResult.setOpenAPI(expectedOpenAPI);

            given(parser.readLocation(eq(testUri.toString()), isNull(), parseOptionsCaptor.capture()))
                    .willReturn(parseResult);

            // When
            var result = openApiReader.read(testUri);

            // Then
            then(result).isSameAs(expectedOpenAPI);

            // Verify parse options are configured correctly
            var capturedParseOptions = parseOptionsCaptor.getValue();
            then(capturedParseOptions.isResolve()).isFalse();
            then(capturedParseOptions.isResolveFully()).isFalse();
            then(capturedParseOptions.isResolveRequestBody()).isFalse();
            then(capturedParseOptions.isResolveResponses()).isFalse();
            then(capturedParseOptions.isResolveCombinators()).isFalse();
        }

        @Test
        void shouldLogWarningsWhenParsingSucceedsWithWarnings(CapturedOutput output) {
            // Given
            var expectedOpenAPI =
                    new OpenAPI().info(new Info().title("Test API").version("1.0.0"));

            var potentialWarnings = List.of(
                    "Warning: Missing description in info object", "Warning: Unused component schema 'UnusedModel'");

            var parseResult = new SwaggerParseResult();
            parseResult.setOpenAPI(expectedOpenAPI);
            parseResult.setMessages(potentialWarnings);

            given(parser.readLocation(eq(testUri.toString()), isNull(), parseOptionsCaptor.capture()))
                    .willReturn(parseResult);

            // When
            var result = openApiReader.read(testUri);

            // Then
            then(result).isEqualTo(expectedOpenAPI);
            then(output.getOut())
                    .contains("Parsing OpenAPI spec resulted in the following warnings: "
                            + "Warning: Missing description in info object; "
                            + "Warning: Unused component schema 'UnusedModel'");
        }

        @Test
        void shouldThrowInvalidOpenAPIExceptionWhenParsingFails(CapturedOutput output) {
            // Given
            var errors = List.of("Error: Invalid schema format", "Error: Missing required field 'info'");

            var parseResult = new SwaggerParseResult();
            parseResult.setOpenAPI(null);
            parseResult.setMessages(errors);

            given(parser.readLocation(eq(testUri.toString()), isNull(), parseOptionsCaptor.capture()))
                    .willReturn(parseResult);

            // When & Then
            thenThrownBy(() -> openApiReader.read(testUri))
                    .isInstanceOf(InvalidOpenApiException.class)
                    .hasMessageContaining("Invalid OpenAPI spec: " + testUri)
                    .hasMessageContaining("Check the validity of the OpenAPI specification")
                    .hasFieldOrPropertyWithValue("messages", errors);

            // Verify error messages are logged
            then(output.getOut())
                    .contains(
                            "Parsing OpenAPI spec resulted in the following errors: Error: Invalid schema format; Error: Missing required field 'info'.");
        }

        @Test
        void shouldThrowInvalidOpenAPIExceptionWhenParsingFailsWithoutErrorMessages() {
            // Given
            var parseResult = new SwaggerParseResult();
            parseResult.setOpenAPI(null);

            given(parser.readLocation(eq(testUri.toString()), isNull(), parseOptionsCaptor.capture()))
                    .willReturn(parseResult);

            // When & Then
            thenThrownBy(() -> openApiReader.read(testUri))
                    .isInstanceOf(InvalidOpenApiException.class)
                    .hasMessageContaining("Invalid OpenAPI spec: " + testUri)
                    .hasFieldOrPropertyWithValue("messages", List.of());
        }

        @Test
        void shouldHandleEmptyErrorMessagesList() {
            // Given
            var parseResult = new SwaggerParseResult();
            parseResult.setOpenAPI(null);
            parseResult.setMessages(List.of());

            given(parser.readLocation(eq(testUri.toString()), isNull(), parseOptionsCaptor.capture()))
                    .willReturn(parseResult);

            // When & Then
            thenThrownBy(() -> openApiReader.read(testUri))
                    .isInstanceOf(InvalidOpenApiException.class)
                    .hasMessageContaining("Invalid OpenAPI spec: " + testUri)
                    .hasFieldOrPropertyWithValue("messages", List.of());
        }
    }

    @Nested
    class IntegrationTests {

        private OpenApiReader realOpenApiReader;
        private WireMockServer wireMockServer;

        @BeforeEach
        void setUp() {
            realOpenApiReader = new OpenApiReader(new OpenAPIV3Parser());
            wireMockServer = new WireMockServer(wireMockConfig().port(0));
            wireMockServer.start();
        }

        @AfterEach
        void tearDown() {
            wireMockServer.stop();
        }

        @Test
        void shouldParseValidOpenAPIYamlFromHttpEndpoint() {
            // Given
            var validOpenAPIYaml =
                    """
                    openapi: 3.1.0
                    info:
                      title: Test API
                      description: A test API for unit testing
                      version: 1.0.0
                    paths:
                      /test:
                        get:
                          summary: Test endpoint
                          responses:
                            '200':
                              description: Success response
                              content:
                                application/json:
                                  schema:
                                    type: object
                                    properties:
                                      message:
                                        type: string
                    """;

            wireMockServer.stubFor(get(urlEqualTo("/api/openapi.yaml"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/yaml")
                            .withBody(validOpenAPIYaml)));

            var apiUri = URI.create(wireMockServer.baseUrl() + "/api/openapi.yaml");

            // When
            var result = realOpenApiReader.read(apiUri);

            // Then
            then(result).isNotNull();
            then(result.getInfo()).isNotNull();
            then(result.getInfo().getTitle()).isEqualTo("Test API");
            then(result.getInfo().getDescription()).isEqualTo("A test API for unit testing");
            then(result.getInfo().getVersion()).isEqualTo("1.0.0");
            then(result.getPaths()).isNotNull();
            then(result.getPaths()).containsKey("/test");
        }

        @Test
        void shouldParseValidOpenAPIJsonFromHttpEndpoint() {
            // Given
            var validOpenAPIJson =
                    """
                    {
                      "openapi": "3.1.0",
                      "info": {
                        "title": "JSON Test API",
                        "description": "A test API in JSON format",
                        "version": "2.0.0"
                      },
                      "paths": {
                        "/json-test": {
                          "post": {
                            "summary": "JSON test endpoint",
                            "requestBody": {
                              "content": {
                                "application/json": {
                                  "schema": {
                                    "type": "object",
                                    "properties": {
                                      "name": {
                                        "type": "string"
                                      }
                                    }
                                  }
                                }
                              }
                            },
                            "responses": {
                              "201": {
                                "description": "Created"
                              }
                            }
                          }
                        }
                      }
                    }
                    """;

            wireMockServer.stubFor(get(urlEqualTo("/api/openapi.json"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(validOpenAPIJson)));

            var apiUri = URI.create(wireMockServer.baseUrl() + "/api/openapi.json");

            // When
            var result = realOpenApiReader.read(apiUri);

            // Then
            then(result).isNotNull();
            then(result.getInfo()).isNotNull();
            then(result.getInfo().getTitle()).isEqualTo("JSON Test API");
            then(result.getInfo().getDescription()).isEqualTo("A test API in JSON format");
            then(result.getInfo().getVersion()).isEqualTo("2.0.0");
            then(result.getPaths()).isNotNull();
            then(result.getPaths()).containsKey("/json-test");
        }

        @Test
        void shouldThrowInvalidOpenAPIExceptionForMalformedDocument() {
            // Given
            var malformedContent = "This is not even YAML or JSON content!";

            wireMockServer.stubFor(get(urlEqualTo("/api/malformed.json"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(malformedContent)));

            var apiUri = URI.create(wireMockServer.baseUrl() + "/api/malformed.json");

            // When & Then
            thenThrownBy(() -> realOpenApiReader.read(apiUri))
                    .isInstanceOf(InvalidOpenApiException.class)
                    .hasMessageContaining("Invalid OpenAPI spec: " + apiUri);
        }

        @Test
        void shouldThrowInvalidOpenAPIExceptionForHttpError() {
            // Given
            wireMockServer.stubFor(get(urlEqualTo("/api/notfound.json"))
                    .willReturn(aResponse().withStatus(404).withBody("Not Found")));

            var apiUri = URI.create(wireMockServer.baseUrl() + "/api/notfound.json");

            // When & Then
            thenThrownBy(() -> realOpenApiReader.read(apiUri))
                    .isInstanceOf(InvalidOpenApiException.class)
                    .hasMessageContaining("Invalid OpenAPI spec: " + apiUri);
        }

        @Test
        void shouldLogWarningsForDocumentWithValidationIssues(CapturedOutput output) {
            // Given
            var openApiWithWarnings =
                    """
                    openapi: 3.0.3
                    info:
                      title: API with Warnings
                      # Missing version which is required
                    paths:
                      invalid-path-without-slash:
                        get:
                          # Missing responses which is required
                    """;

            wireMockServer.stubFor(get(urlEqualTo("/api/warnings.yaml"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/yaml")
                            .withBody(openApiWithWarnings)));

            var apiUri = URI.create(wireMockServer.baseUrl() + "/api/warnings.yaml");

            // When
            var result = realOpenApiReader.read(apiUri);

            // Then
            then(result).isNotNull();
            then(result.getInfo()).isNotNull();
            then(result.getInfo().getTitle()).isEqualTo("API with Warnings");

            // Verify warning messages are logged
            then(output.getOut())
                    .contains("Parsing OpenAPI spec resulted in the following warnings: "
                            + "attribute paths.'invalid-path-without-slash'.get is not of type `object`; "
                            + "attribute info.version is missing; "
                            + "paths. Resource invalid-path-without-slash should start with /.");
        }

        @Test
        void shouldNotResolveReferences() {
            // Given
            var openApiWithReferences =
                    """
                    {
                      "openapi": "3.0.3",
                      "info": {
                        "title": "API with References",
                        "version": "1.0.0"
                      },
                      "paths": {
                        "/users": {
                          "post": {
                            "summary": "Create user",
                            "requestBody": {
                              "$ref": "#/components/requestBodies/UserRequest"
                            },
                            "responses": {
                              "201": {
                                "$ref": "#/components/responses/UserResponse"
                              },
                              "400": {
                                "$ref": "#/components/responses/ErrorResponse"
                              }
                            }
                          }
                        },
                        "/users/{id}": {
                          "get": {
                            "summary": "Get user by ID",
                            "parameters": [
                              {
                                "$ref": "#/components/parameters/UserIdParam"
                              }
                            ],
                            "responses": {
                              "200": {
                                "$ref": "#/components/responses/UserResponse"
                              }
                            }
                          }
                        }
                      },
                      "components": {
                        "schemas": {
                          "User": {
                            "type": "object",
                            "properties": {
                              "id": {
                                "type": "integer"
                              },
                              "name": {
                                "type": "string"
                              },
                              "email": {
                                "type": "string"
                              }
                            }
                          },
                          "Error": {
                            "type": "object",
                            "properties": {
                              "code": {
                                "type": "integer"
                              },
                              "message": {
                                "type": "string"
                              }
                            }
                          }
                        },
                        "requestBodies": {
                          "UserRequest": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "$ref": "#/components/schemas/User"
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "UserResponse": {
                            "description": "User response",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "$ref": "#/components/schemas/User"
                                }
                              }
                            }
                          },
                          "ErrorResponse": {
                            "description": "Error response",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "$ref": "#/components/schemas/Error"
                                }
                              }
                            }
                          }
                        },
                        "parameters": {
                          "UserIdParam": {
                            "name": "id",
                            "in": "path",
                            "required": true,
                            "schema": {
                              "type": "integer"
                            }
                          }
                        }
                      }
                    }
                    """;

            wireMockServer.stubFor(get(urlEqualTo("/api/with-refs.json"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(openApiWithReferences)));

            var apiUri = URI.create(wireMockServer.baseUrl() + "/api/with-refs.json");

            // When
            var result = realOpenApiReader.read(apiUri);

            // Then
            then(result).isNotNull();
            then(result.getInfo().getTitle()).isEqualTo("API with References");

            // Verify that $ref references are NOT resolved (remain as reference objects)
            var postOperation = result.getPaths().get("/users").getPost();
            then(postOperation).isNotNull();

            // Check that request body reference is not resolved
            var requestBody = postOperation.getRequestBody();
            then(requestBody.get$ref()).isEqualTo("#/components/requestBodies/UserRequest");
            then(requestBody.getContent()).isNull(); // Should be null since it's not resolved

            // Check that response references are not resolved
            var createdResponse = postOperation.getResponses().get("201");
            then(createdResponse.get$ref()).isEqualTo("#/components/responses/UserResponse");
            then(createdResponse.getDescription()).isNull(); // Should be null since it's not resolved

            var errorResponse = postOperation.getResponses().get("400");
            then(errorResponse.get$ref()).isEqualTo("#/components/responses/ErrorResponse");
            then(errorResponse.getDescription()).isNull(); // Should be null since it's not resolved

            // Check parameter reference is not resolved
            var getOperation = result.getPaths().get("/users/{id}").getGet();
            then(getOperation).isNotNull();
            var parameter = getOperation.getParameters().getFirst();
            then(parameter.get$ref()).isEqualTo("#/components/parameters/UserIdParam");
            then(parameter.getName()).isNull(); // Should be null since it's not resolved

            // Verify that components section still exists with all definitions
            then(result.getComponents()).isNotNull();
            then(result.getComponents().getSchemas()).containsKeys("User", "Error");
            then(result.getComponents().getRequestBodies()).containsKey("UserRequest");
            then(result.getComponents().getResponses()).containsKeys("UserResponse", "ErrorResponse");
            then(result.getComponents().getParameters()).containsKey("UserIdParam");
        }
    }
}
