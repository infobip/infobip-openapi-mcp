package com.infobip.openapi.mcp.openapi.tool;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import com.infobip.openapi.mcp.openapi.schema.InputSchemaComposer;
import com.infobip.openapi.mcp.openapi.tool.exception.ToolRegistrationException;
import com.infobip.openapi.mcp.openapi.tool.naming.OperationIdStrategy;
import com.infobip.openapi.mcp.util.OpenApiMapperFactory;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @Mock
    private OpenApiRegistry openApiRegistry;

    @Mock
    private ToolHandler toolHandler;

    private final OperationIdStrategy namingStrategy = new OperationIdStrategy();
    private final InputSchemaComposer inputSchemaComposer = new InputSchemaComposer(new OpenApiMcpProperties());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();
    private final OpenApiMapperFactory mapperFactory = new OpenApiMapperFactory();

    private ToolRegistry toolRegistry;
    private OpenApiMcpProperties properties;

    @BeforeEach
    void setUp() {
        // Default properties with prepend enabled
        var tools = new OpenApiMcpProperties.Tools(null, null, null, true, null, null);
        properties = new OpenApiMcpProperties(null, null, null, null, null, null, tools, null);
        toolRegistry = new ToolRegistry(
                openApiRegistry, namingStrategy, inputSchemaComposer, toolHandler, mapperFactory, properties);
    }

    @Test
    void shouldReturnEmptyListWhenNoPathsInOpenAPI() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenPathsAreEmpty() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {}
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldCreateToolSpecificationForGetOperationWithQueryParameter() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "description": "Get all users",
                    "parameters": [
                      {
                        "name": "limit",
                        "in": "query",
                        "description": "Maximum number of users to return",
                        "schema": {
                          "type": "string"
                        }
                      }
                    ]
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().name()).isEqualTo("getUsers");
        then(registeredTool.tool().description()).isEqualTo("Get all users");

        var expectedSchema = """
            {
              "type": "object",
              "properties": {
                "limit": {
                  "type": "string",
                  "description": "Maximum number of users to return"
                }
              }
            }
            """;
        assertJsonEquals(expectedSchema, writeInputSchema(registeredTool.tool().inputSchema()));
    }

    @Test
    void shouldCreateToolSpecificationForPostOperationWithRequestBody() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "post": {
                    "operationId": "createUser",
                    "description": "Create a new user",
                    "requestBody": {
                      "required": true,
                      "content": {
                        "application/json": {
                          "schema": {
                            "type": "object",
                            "properties": {
                              "name": {
                                "type": "string",
                                "description": "User's full name"
                              }
                            },
                            "required": ["name"]
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().name()).isEqualTo("createUser");
        then(registeredTool.tool().description()).isEqualTo("Create a new user");

        var expectedSchema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "User's full name"
                }
              },
              "required": ["name"]
            }
            """;
        assertJsonEquals(expectedSchema, writeInputSchema(registeredTool.tool().inputSchema()));
    }

    @Test
    void shouldCreateMultipleToolSpecificationsForMultipleOperations() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "description": "Get all users",
                    "parameters": [
                      {
                        "name": "limit",
                        "in": "query",
                        "schema": {
                          "type": "string"
                        }
                      }
                    ]
                  },
                  "post": {
                    "operationId": "createUser",
                    "description": "Create a new user",
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
                    }
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(2);
        then(result)
                .extracting(registeredTool -> registeredTool.tool().name())
                .containsExactlyInAnyOrder("getUsers", "createUser");

        var getUsersTool = result.stream()
                .filter(registeredTool ->
                        "getUsers".equals(registeredTool.tool().name()))
                .findFirst()
                .orElseThrow();
        var expectedGetUsersSchema = """
            {
              "type": "object",
              "properties": {
                "limit": {
                  "type": "string"
                }
              }
            }
            """;
        assertJsonEquals(
                expectedGetUsersSchema, writeInputSchema(getUsersTool.tool().inputSchema()));

        var createUserTool = result.stream()
                .filter(registeredTool ->
                        "createUser".equals(registeredTool.tool().name()))
                .findFirst()
                .orElseThrow();
        var expectedCreateUserSchema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                }
              }
            }
            """;
        assertJsonEquals(
                expectedCreateUserSchema, writeInputSchema(createUserTool.tool().inputSchema()));
    }

    @Test
    void shouldThrowToolRegistrationExceptionWhenOperationIdIsMissing() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "description": "Get all users"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When & Then
        thenThrownBy(() -> toolRegistry.getTools())
                .isInstanceOf(ToolRegistrationException.class)
                .hasMessageContaining("Unable to register tool for operation: GET /users")
                .hasMessageContaining("Operation ID is null")
                .hasCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleOperationsWithoutDescriptions() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().description()).isNull();
    }

    @Test
    void shouldSetToolTitleFromOperationSummary() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Retrieve Users",
                    "description": "Get all users from the system"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().name()).isEqualTo("getUsers");
        then(registeredTool.tool().title()).isEqualTo("Retrieve Users");
        then(registeredTool.tool().description()).isEqualTo("# Retrieve Users\n\nGet all users from the system");
    }

    @Test
    void shouldHandleOperationsWithoutSummary() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "description": "Get all users from the system"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().name()).isEqualTo("getUsers");
        then(registeredTool.tool().title()).isEqualTo("getUsers"); // Falls back to name
        then(registeredTool.tool().description()).isEqualTo("Get all users from the system");
    }

    @Test
    void shouldSetDifferentTitlesForMultipleOperations() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "List All Users",
                    "description": "Retrieve all users"
                  },
                  "post": {
                    "operationId": "createUser",
                    "summary": "Create New User",
                    "description": "Create a new user account"
                  }
                },
                "/users/{id}": {
                  "get": {
                    "operationId": "getUserById",
                    "description": "Get user by ID"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result)
                .hasSize(3)
                .extracting(RegisteredTool::tool)
                .extracting(McpSchema.Tool::name, McpSchema.Tool::title, McpSchema.Tool::description)
                .containsExactlyInAnyOrder(
                        tuple("getUsers", "List All Users", "# List All Users\n\nRetrieve all users"),
                        tuple("createUser", "Create New User", "# Create New User\n\nCreate a new user account"),
                        tuple("getUserById", "getUserById", "Get user by ID"));
    }

    @Test
    void shouldHandleOperationWithoutParametersOrRequestBody() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/health": {
                  "get": {
                    "operationId": "healthCheck",
                    "description": "Health check endpoint"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().name()).isEqualTo("healthCheck");

        var expectedEmptyObjectSchema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        then(registeredTool.tool().inputSchema()).isNotNull();
        assertJsonEquals(
                expectedEmptyObjectSchema,
                writeInputSchema(registeredTool.tool().inputSchema()));
    }

    @Test
    void shouldUseCorrectMapperForOpenAPI30() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "post": {
                    "operationId": "createUser",
                    "description": "Create user with OpenAPI 3.0 nullable fields",
                    "requestBody": {
                      "required": true,
                      "content": {
                        "application/json": {
                          "schema": {
                            "type": "object",
                            "properties": {
                              "email": {
                                "type": "string",
                                "format": "email",
                                "description": "User email address"
                              },
                              "middleName": {
                                "type": "string",
                                "nullable": true,
                                "description": "Optional middle name"
                              },
                              "age": {
                                "type": "integer",
                                "minimum": 18,
                                "maximum": 100,
                                "example": 25
                              }
                            },
                            "required": ["email"]
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var expectedSchema = """
            {
              "type": "object",
              "properties": {
                "email": {
                  "type": "string",
                  "format": "email",
                  "description": "User email address"
                },
                "middleName": {
                  "type": "string",
                  "nullable": true,
                  "description": "Optional middle name"
                },
                "age": {
                  "type": "integer",
                  "minimum": 18,
                  "maximum": 100,
                  "example": 25
                }
              },
              "required": ["email"]
            }
            """;
        assertJsonEquals(
                expectedSchema, writeInputSchema(result.getFirst().tool().inputSchema()));
    }

    @Test
    void shouldUseCorrectMapperForOpenAPI31() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "post": {
                    "operationId": "createUser",
                    "description": "Create user with OpenAPI 3.1 type arrays and JSON Schema features",
                    "requestBody": {
                      "required": true,
                      "content": {
                        "application/json": {
                          "schema": {
                            "type": "object",
                            "properties": {
                              "email": {
                                "type": "string",
                                "format": "email",
                                "description": "User email address"
                              },
                              "middleName": {
                                "type": ["string", "null"],
                                "description": "Optional middle name using type array"
                              },
                              "age": {
                                "type": "integer",
                                "minimum": 18,
                                "maximum": 100,
                                "examples": [25, 30, 45]
                              },
                              "status": {
                                "const": "active",
                                "description": "User status (const value)"
                              },
                              "preferences": {
                                "type": "object",
                                "properties": {
                                  "theme": {
                                    "type": "string",
                                    "enum": ["light", "dark"]
                                  }
                                },
                                "additionalProperties": false
                              }
                            },
                            "required": ["email", "status"],
                            "additionalProperties": false
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var expectedSchema = """
            {
              "type": "object",
              "properties": {
                "email": {
                  "type": "string",
                  "format": "email",
                  "description": "User email address"
                },
                "middleName": {
                  "type": ["string", "null"],
                  "description": "Optional middle name using type array"
                },
                "age": {
                  "type": "integer",
                  "minimum": 18,
                  "maximum": 100,
                  "examples": [25, 30, 45]
                },
                "status": {
                  "const": "active",
                  "description": "User status (const value)"
                },
                "preferences": {
                  "type": "object",
                  "properties": {
                    "theme": {
                      "type": "string",
                      "enum": ["light", "dark"]
                    }
                  },
                  "additionalProperties": false
                }
              },
              "required": ["email", "status"],
              "additionalProperties": false
            }
            """;
        assertJsonEquals(
                expectedSchema, writeInputSchema(result.getFirst().tool().inputSchema()));
    }

    @Test
    void shouldInvokeToolHandlerWithDecomposedArguments() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users/{userId}": {
                  "get": {
                    "operationId": "getUser",
                    "description": "Get user by ID",
                    "parameters": [
                      {
                        "name": "userId",
                        "in": "path",
                        "required": true,
                        "schema": {
                          "type": "string"
                        }
                      },
                      {
                        "name": "include",
                        "in": "query",
                        "schema": {
                          "type": "string"
                        }
                      }
                    ]
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        var expectedResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("Success")))
                .build();

        given(toolHandler.handleToolCall(any(), any(), any())).willReturn(expectedResult);

        // When
        var tools = toolRegistry.getTools();
        then(tools).hasSize(1);

        var registeredTool = tools.getFirst();
        var callToolRequest = McpSchema.CallToolRequest.builder()
                .name("getUser")
                .arguments(Map.of(
                        "userId", "123",
                        "include", "profile"))
                .build();

        var context = createTestContext();
        var result = registeredTool.toolHandler().apply(callToolRequest, context);

        // Then
        then(result).isEqualTo(expectedResult);

        // Verify toolHandler was called with the correct arguments
        ArgumentCaptor<FullOperation> operationCaptor = ArgumentCaptor.forClass(FullOperation.class);
        ArgumentCaptor<DecomposedRequestData> argumentsCaptor = ArgumentCaptor.forClass(DecomposedRequestData.class);
        ArgumentCaptor<McpRequestContext> contextCaptor = ArgumentCaptor.forClass(McpRequestContext.class);

        BDDMockito.then(toolHandler)
                .should()
                .handleToolCall(operationCaptor.capture(), argumentsCaptor.capture(), contextCaptor.capture());

        var capturedOperation = operationCaptor.getValue();
        then(capturedOperation.operation().getOperationId()).isEqualTo("getUser");
        then(capturedOperation.path()).isEqualTo("/users/{userId}");
        then(capturedOperation.method()).isEqualTo(io.swagger.v3.oas.models.PathItem.HttpMethod.GET);

        var capturedArguments = argumentsCaptor.getValue();
        then(capturedArguments.parametersByType().path()).containsOnly(entry("userId", "123"));
        then(capturedArguments.parametersByType().query()).containsOnly(entry("include", "profile"));
        then(capturedArguments.requestBody()).isNull();
    }

    @Test
    void shouldPrependSummaryToDescriptionWhenBothPresent() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Retrieve Users",
                    "description": "Get all users from the system"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().name()).isEqualTo("getUsers");
        then(registeredTool.tool().title()).isEqualTo("Retrieve Users");
        then(registeredTool.tool().description()).isEqualTo("# Retrieve Users\n\nGet all users from the system");
    }

    @Test
    void shouldPrependSummaryToDescriptionWhenFeatureEnabled() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Hello",
                    "description": "This is a description"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("Hello");
        then(registeredTool.tool().description()).isEqualTo("# Hello\n\nThis is a description");
    }

    @Test
    void shouldNotPrependSummaryToDescriptionWhenFeatureDisabled() {
        // Given
        var tools = new OpenApiMcpProperties.Tools(null, null, null, false, null, null);
        properties = new OpenApiMcpProperties(null, null, null, null, null, null, tools, null);
        toolRegistry = new ToolRegistry(
                openApiRegistry, namingStrategy, inputSchemaComposer, toolHandler, mapperFactory, properties);

        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Hello",
                    "description": "This is a description"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("Hello");
        then(registeredTool.tool().description()).isEqualTo("This is a description");
    }

    @Test
    void shouldUseSummaryAsDescriptionWhenFeatureDisabledAndOnlySummaryPresent() {
        // Given
        var tools = new OpenApiMcpProperties.Tools(null, null, null, false, null, null);
        properties = new OpenApiMcpProperties(null, null, null, null, null, null, tools, null);
        toolRegistry = new ToolRegistry(
                openApiRegistry, namingStrategy, inputSchemaComposer, toolHandler, mapperFactory, properties);

        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Hello"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("Hello");
        then(registeredTool.tool().description()).isEqualTo("Hello");
    }

    @Test
    void shouldReturnDescriptionWhenOnlyDescriptionPresent() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "description": "This is a description"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("getUsers"); // Falls back to name
        then(registeredTool.tool().description()).isEqualTo("This is a description");
    }

    @Test
    void shouldReturnDescriptionWhenSummaryIsEmpty() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "",
                    "description": "This is a description"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("getUsers"); // Falls back to name when empty
        then(registeredTool.tool().description()).isEqualTo("This is a description");
    }

    @Test
    void shouldReturnDescriptionWhenSummaryIsBlank() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "   ",
                    "description": "This is a description"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("getUsers"); // Falls back to name when blank
        then(registeredTool.tool().description()).isEqualTo("This is a description");
    }

    @Test
    void shouldUseSummaryAsDescriptionWhenOnlySummaryPresent() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Hello"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("Hello");
        then(registeredTool.tool().description())
                .isEqualTo("Hello"); // Summary becomes description when no description exists
    }

    @Test
    void shouldUseSummaryAsDescriptionWhenDescriptionIsEmpty() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Hello",
                    "description": ""
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("Hello");
        then(registeredTool.tool().description())
                .isEqualTo("Hello"); // Empty description treated as absent, summary used instead
    }

    @Test
    void shouldUseSummaryAsDescriptionWhenDescriptionIsBlank() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "Hello",
                    "description": "   "
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result).hasSize(1);
        var registeredTool = result.getFirst();
        then(registeredTool.tool().title()).isEqualTo("Hello");
        then(registeredTool.tool().description())
                .isEqualTo("Hello"); // Blank description treated as absent, summary used instead
    }

    @Test
    void shouldPrependSummaryForMultipleOperations() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "List Users",
                    "description": "Retrieve all users"
                  },
                  "post": {
                    "operationId": "createUser",
                    "summary": "Create User",
                    "description": "Create a new user"
                  }
                },
                "/products": {
                  "get": {
                    "operationId": "getProducts",
                    "summary": "List Products",
                    "description": "Retrieve all products"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result)
                .hasSize(3)
                .extracting(RegisteredTool::tool)
                .extracting(McpSchema.Tool::name, McpSchema.Tool::title, McpSchema.Tool::description)
                .containsExactlyInAnyOrder(
                        tuple("getUsers", "List Users", "# List Users\n\nRetrieve all users"),
                        tuple("createUser", "Create User", "# Create User\n\nCreate a new user"),
                        tuple("getProducts", "List Products", "# List Products\n\nRetrieve all products"));
    }

    @Test
    void shouldHandleMixedOperationsWithAndWithoutSummary() {
        // Given
        var openApi = parseOpenAPI("""
            {
              "openapi": "3.1.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "summary": "List Users",
                    "description": "Retrieve all users"
                  },
                  "post": {
                    "operationId": "createUser",
                    "description": "Create a new user"
                  }
                }
              }
            }
            """);
        given(openApiRegistry.openApi()).willReturn(openApi);

        // When
        var result = toolRegistry.getTools();

        // Then
        then(result)
                .hasSize(2)
                .extracting(RegisteredTool::tool)
                .extracting(McpSchema.Tool::name, McpSchema.Tool::title, McpSchema.Tool::description)
                .containsExactlyInAnyOrder(
                        tuple("getUsers", "List Users", "# List Users\n\nRetrieve all users"),
                        tuple("createUser", "createUser", "Create a new user"));
    }

    private OpenAPI parseOpenAPI(String jsonSpec) {
        return parser.readContents(jsonSpec).getOpenAPI();
    }

    private String writeInputSchema(McpSchema.JsonSchema schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write JSON schema", e);
        }
    }

    private void assertJsonEquals(String expected, String actual) {
        try {
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to compare JSON schemas", e);
        }
    }

    /**
     * Helper method to create a simple test context without HTTP request.
     */
    private McpRequestContext createTestContext() {
        return new McpRequestContext();
    }
}
