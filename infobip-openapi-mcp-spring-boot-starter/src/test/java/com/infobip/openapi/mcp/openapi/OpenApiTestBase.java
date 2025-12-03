package com.infobip.openapi.mcp.openapi;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that require OpenAPI specification mocking.
 * Provides a shared WireMock server with dynamic port allocation and common test infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class OpenApiTestBase {

    protected static WireMockServer staticWireMockServer;
    private static volatile boolean serverInitialized = false;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ApplicationContext applicationContext;

    protected static WireMockServer getStaticWireMockServer() {
        return staticWireMockServer;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Static server is needed here because @DynamicPropertySource must be static
        initializeWireMockServer();

        // Setup OpenAPI mock immediately
        setupOpenAPIMock(staticWireMockServer);

        // Configure both required OpenAPI properties with explicit string values
        String baseUrl = staticWireMockServer.baseUrl();
        registry.add("infobip.openapi.mcp.open-api-url", () -> baseUrl + "/openapi.json");
        registry.add("infobip.openapi.mcp.api-base-url", () -> baseUrl);
    }

    private static synchronized void initializeWireMockServer() {
        if (!serverInitialized || staticWireMockServer == null || !staticWireMockServer.isRunning()) {
            if (staticWireMockServer != null && !staticWireMockServer.isRunning()) {
                staticWireMockServer.start();
            } else if (staticWireMockServer == null) {
                staticWireMockServer = new WireMockServer(0);
                staticWireMockServer.start();
            }
            serverInitialized = true;
        }
    }

    @BeforeEach
    void makeSureWireMockIsRunning() {
        // Ensure server is still running before each test
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }
        staticWireMockServer.resetAll();
        setupOpenAPIMock(staticWireMockServer);
    }

    @AfterEach
    void resetWireMock() {
        // Only reset, don't stop the server
        if (staticWireMockServer != null && staticWireMockServer.isRunning()) {
            staticWireMockServer.resetAll();
        }
    }

    // Add shutdown hook for proper cleanup
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (staticWireMockServer != null && staticWireMockServer.isRunning()) {
                staticWireMockServer.stop();
            }
        }));
    }

    /**
     * Sets up the standard OpenAPI specification mock.
     * Override this method if you need a different OpenAPI spec.
     */
    protected static void setupOpenAPIMock(WireMockServer wireMockServer) {
        wireMockServer.stubFor(get(urlEqualTo("/openapi.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getOpenAPISpec())));
    }

    /**
     * Sets up the custom OpenAPI specification mock.
     */
    protected static void setupCustomOpenAPIMock(WireMockServer wireMockServer, String customOpenApiSpec) {
        wireMockServer.stubFor(get(urlEqualTo("/openapi.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(customOpenApiSpec)));
    }

    /**
     * Returns the default OpenAPI specification JSON with multiple operations for comprehensive testing.
     * This spec includes GET and POST operations with parameters, request bodies, and path parameters.
     * Override this method if you need a different spec.
     */
    protected static String getOpenAPISpec() {
        return """
                {
                    "openapi": "3.1.0",
                    "info": {
                        "title": "User management",
                        "version": "1.2.7",
                        "summary": "Manage users",
                        "description": "Fetch **paginated** list of multiple users or one by one."
                    },
                    "paths": {
                        "/users": {
                            "get": {
                                "operationId": "get-users",
                                "description": "Get all users",
                                "parameters": [
                                    {
                                        "name": "limit",
                                        "in": "query",
                                        "schema": {
                                            "type": "integer"
                                        }
                                    }
                                ],
                                "responses": {
                                    "200": {
                                        "description": "OK"
                                    }
                                }
                            },
                            "post": {
                                "operationId": "create-user",
                                "description": "Create a new user",
                                "requestBody": {
                                    "required": true,
                                    "content": {
                                        "application/json": {
                                            "schema": {
                                                "type": "object",
                                                "properties": {
                                                    "name": {
                                                        "type": "string"
                                                    }
                                                },
                                                "required": ["name"]
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
                        },
                        "/users/{userId}": {
                            "get": {
                                "operationId": "get-user-by-id",
                                "description": "Get user by ID",
                                "parameters": [
                                    {
                                        "name": "userId",
                                        "in": "path",
                                        "required": true,
                                        "schema": {
                                            "type": "string"
                                        }
                                    }
                                ],
                                "responses": {
                                    "200": {
                                        "description": "OK"
                                    }
                                }
                            }
                        }
                    }
                }
                """;
    }

    /**
     * Check if a given property is enabled in the application context.
     * Defaults to false if the property is not set.
     */
    protected boolean givenPropertyEnabled(String propertyName) {
        return applicationContext.getEnvironment().getProperty(propertyName + ".enabled", Boolean.class, false);
    }
}
