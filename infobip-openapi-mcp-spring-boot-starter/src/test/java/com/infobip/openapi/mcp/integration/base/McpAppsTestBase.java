package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.apps.AppMeta;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class McpAppsTestBase extends IntegrationTestBase {

    private static final String DASHBOARD_HTML = """
            <!doctype html>
            <html><body><h1>Forecast dashboard</h1></body></html>""";

    private static final Map<String, Object> GIVEN_UI_META = Map.of(
            "csp",
            Map.of("connectDomains", List.of("https://api.example.com")),
            "permissions",
            Map.of("camera", Map.of()),
            "domain",
            "sandbox.example.com",
            "prefersBorder",
            true);

    @DynamicPropertySource
    static void setupAppSpec(DynamicPropertyRegistry registry) {
        if (staticWireMockServer == null) {
            staticWireMockServer = new WireMockServer(0);
        }
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }
        stubAppOpenApiSpec();
    }

    @BeforeEach
    void setupAppStubs() {
        stubAppOpenApiSpec();
        staticWireMockServer.stubFor(get(urlPathEqualTo("/dashboard"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(DASHBOARD_HTML)));
        staticWireMockServer.stubFor(
                get(urlPathEqualTo("/forecast/object")).willReturn(okJson("{\"city\": \"Vim\", \"temperature\": 21}")));
        staticWireMockServer.stubFor(get(urlPathEqualTo("/forecast/list"))
                .willReturn(okJson("[{\"hour\": 0, \"temperature\": 18}, {\"hour\": 1, \"temperature\": 17}]")));
        staticWireMockServer.stubFor(get(urlPathEqualTo("/plain")).willReturn(okJson("{\"status\": \"UP\"}")));
    }

    private static void stubAppOpenApiSpec() {
        staticWireMockServer.stubFor(get(urlEqualTo("/openapi.json"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getAppOpenAPISpec())));
    }

    @Test
    void shouldListAppResourceWithAppMimeTypeAndUiMeta() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.listResources();

            // Then
            var appResource = result.resources().stream()
                    .filter(resource -> "ui://dashboard".equals(resource.uri()))
                    .findFirst()
                    .orElseThrow();
            then(appResource.name()).isEqualTo("getdashboard");
            then(appResource.mimeType()).isEqualTo(AppMeta.MIME_TYPE);
            then(appResource.meta()).containsEntry(AppMeta.UI_META_KEY, GIVEN_UI_META);
        });
    }

    @Test
    void shouldReadAppResourceWithForcedMimeTypeAndUiMeta() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var request = new McpSchema.ReadResourceRequest("ui://dashboard");

            // When
            var result = givenClient.readResource(request);

            // Then
            then(result.contents()).hasSize(1);
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.uri()).isEqualTo("ui://dashboard");
            then(contents.mimeType()).isEqualTo(AppMeta.MIME_TYPE);
            then(contents.text()).isEqualTo(DASHBOARD_HTML);
            then(contents.meta()).containsEntry(AppMeta.UI_META_KEY, GIVEN_UI_META);
        });
    }

    @Test
    void shouldLinkAppToolsToUiResourceViaMeta() {
        withInitializedMcpClient(givenClient -> {
            // When
            var tools = givenClient.listTools().tools();

            // Then
            var summaryTool = tools.stream()
                    .filter(tool -> "getforecastsummary".equals(tool.name()))
                    .findFirst()
                    .orElseThrow();
            then(summaryTool.meta()).containsEntry(AppMeta.DEPRECATED_TOOL_RESOURCE_URI_KEY, "ui://dashboard");
            then(castUi(summaryTool.meta()))
                    .containsEntry("resourceUri", "ui://dashboard")
                    .containsEntry("visibility", List.of("model", "app"));

            var listTool = tools.stream()
                    .filter(tool -> "getforecastlist".equals(tool.name()))
                    .findFirst()
                    .orElseThrow();
            then(castUi(listTool.meta())).containsEntry("resourceUri", "ui://dashboard");
            then(castUi(listTool.meta())).doesNotContainKey("visibility");

            var plainTool = tools.stream()
                    .filter(tool -> "getplain".equals(tool.name()))
                    .findFirst()
                    .orElseThrow();
            then(plainTool.meta()).isNull();
        });
    }

    @Test
    void shouldReturnStructuredContentForObjectResponseOfAppTool() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("getforecastsummary")
                    .arguments(Map.of())
                    .build());

            // Then
            then(result.isError()).isFalse();
            then(result.structuredContent()).isEqualTo(Map.of("city", "Vim", "temperature", 21));
            then(((McpSchema.TextContent) result.content().getFirst()).text())
                    .isEqualTo("{\"city\": \"Vim\", \"temperature\": 21}");
        });
    }

    @Test
    void shouldWrapArrayResponseInStructuredContentOfAppTool() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("getforecastlist")
                    .arguments(Map.of())
                    .build());

            // Then
            then(result.isError()).isFalse();
            then(result.structuredContent())
                    .isEqualTo(Map.of(
                            "result",
                            List.of(Map.of("hour", 0, "temperature", 18), Map.of("hour", 1, "temperature", 17))));
        });
    }

    @Test
    void shouldNotReturnStructuredContentForPlainTool() {
        withInitializedMcpClient(givenClient -> {
            // When
            var result = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("getplain")
                    .arguments(Map.of())
                    .build());

            // Then
            then(result.isError()).isFalse();
            then(result.structuredContent()).isNull();
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castUi(Map<String, Object> meta) {
        return (Map<String, Object>) meta.get(AppMeta.UI_META_KEY);
    }

    private static String getAppOpenAPISpec() {
        return """
                {
                    "openapi": "3.1.0",
                    "info": {
                        "title": "MCP Apps test API",
                        "version": "1.0.0"
                    },
                    "paths": {
                        "/dashboard": {
                            "get": {
                                "operationId": "getDashboard",
                                "description": "Renders the forecast dashboard",
                                "x-mcp-app-resource": {
                                    "uri": "ui://dashboard",
                                    "csp": { "connectDomains": ["https://api.example.com"] },
                                    "permissions": { "camera": {} },
                                    "domain": "sandbox.example.com",
                                    "prefersBorder": true
                                },
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "text/html": { "schema": { "type": "string" } }
                                        }
                                    }
                                }
                            }
                        },
                        "/forecast/object": {
                            "get": {
                                "operationId": "getForecastSummary",
                                "description": "Get the forecast summary",
                                "x-mcp-app": {
                                    "resourceUri": "ui://dashboard",
                                    "visibility": ["model", "app"]
                                },
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "application/json": {
                                                "schema": {
                                                    "type": "object",
                                                    "properties": {
                                                        "city": { "type": "string" },
                                                        "temperature": { "type": "integer" }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        "/forecast/list": {
                            "get": {
                                "operationId": "getForecastList",
                                "description": "Get the hourly forecast",
                                "x-mcp-app": { "resourceUri": "ui://dashboard" },
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "application/json": {
                                                "schema": {
                                                    "type": "array",
                                                    "items": { "type": "object" }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        "/plain": {
                            "get": {
                                "operationId": "getPlain",
                                "description": "A tool without MCP Apps metadata",
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "application/json": { "schema": { "type": "object" } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """;
    }
}
