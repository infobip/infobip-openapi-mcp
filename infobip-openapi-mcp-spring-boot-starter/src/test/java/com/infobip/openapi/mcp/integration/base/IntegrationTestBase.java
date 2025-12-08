package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.web.AuthenticationTestBase;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.tool.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpError;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD) // Needed for tool reloading
abstract class IntegrationTestBase extends AuthenticationTestBase {

    @Autowired
    private OpenApiRegistry openApiRegistry;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private McpRequestContextFactory contextFactory;

    @Autowired(required = false)
    McpSyncServer mcpSyncServer;

    @Autowired(required = false)
    McpStatelessSyncServer mcpStatelessSyncServer;

    @Autowired
    McpServerProperties mcpServerProperties;

    @Autowired(required = false)
    List<ToolCallFilter> toolCallFilters;

    protected McpSyncClient mcpSyncClient = null;

    @BeforeEach
    void setUp() {
        this.mcpSyncClient = givenMcpClientWithAuthHeader("");
    }

    @AfterEach
    void tearDown() {
        if (this.mcpSyncServer != null) {
            this.mcpSyncServer.closeGracefully();
            this.mcpSyncClient.close();
        }
        if (this.mcpStatelessSyncServer != null) {
            this.mcpStatelessSyncServer.close();
        }
        this.mcpSyncClient = null;
    }

    protected McpSyncClient givenMcpClientWithAuthHeader(String authHeaderValue) {
        var requestBuilder = HttpRequest.newBuilder();
        if (!authHeaderValue.isBlank()) {
            requestBuilder = requestBuilder.header("Authorization", authHeaderValue);
        }

        var baseUrl = "http://localhost:" + port + "/mcp";
        var transport =
                switch (mcpServerProperties.getProtocol()) {
                    case STREAMABLE, STATELESS ->
                        HttpClientStreamableHttpTransport.builder(baseUrl)
                                .requestBuilder(requestBuilder)
                                .build();
                    case SSE ->
                        HttpClientSseClientTransport.builder(baseUrl)
                                .requestBuilder(requestBuilder)
                                .build();
                };

        return McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(2))
                .requestTimeout(Duration.ofSeconds(2))
                .build();
    }

    protected void withInitializedMcpClient(Consumer<McpSyncClient> consumer) {
        try (var client = mcpSyncClient) {
            client.initialize();
            consumer.accept(client);
            client.closeGracefully();
        }
    }

    protected void givenOpenAPISpecification(String openApiSpecPath) {
        // Clear tools first
        toolRegistry.getTools().forEach(this::removeTool);

        // Stub OpenAPI spec endpoint
        staticWireMockServer.stubFor(get(urlEqualTo("/openapi.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(loadOpenApiSpec(openApiSpecPath))));

        // Reload OpenAPI and tools
        openApiRegistry.reload();
        toolRegistry.getTools().forEach(this::addTool);

        // Notify MCP server about tool changes
        if (mcpSyncServer != null) {
            mcpSyncServer.notifyToolsListChanged();
        }
    }

    private String loadOpenApiSpec(String path) {
        try (var inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("OpenAPI specification file not found: " + path);
            }
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OpenAPI specification from path: " + path, e);
        }
    }

    private void addTool(RegisteredTool tool) {
        try {
            var chainFactory = new OrderingToolCallFilterChainFactory(
                    tool, Objects.requireNonNullElseGet(toolCallFilters, List::of));
            if (this.mcpSyncServer != null) {
                this.mcpSyncServer.addTool(McpServerFeatures.SyncToolSpecification.builder()
                        .tool(tool.tool())
                        .callHandler((mcpSyncServerExchange, callToolRequest) -> {
                            var context = contextFactory.forStatefulTransport(
                                    mcpSyncServerExchange, callToolRequest.name(), givenFullOperation());
                            return chainFactory.get().doFilter(context, callToolRequest);
                        })
                        .build());
            } else if (this.mcpStatelessSyncServer != null) {
                this.mcpStatelessSyncServer.addTool(McpStatelessServerFeatures.SyncToolSpecification.builder()
                        .tool(tool.tool())
                        .callHandler((mcpTransportContext, callToolRequest) -> {
                            var context = contextFactory.forStatelessTransport(
                                    mcpTransportContext, callToolRequest.name(), givenFullOperation());
                            return chainFactory.get().doFilter(context, callToolRequest);
                        })
                        .build());
            }
        } catch (McpError ignored) {
        }
    }

    private FullOperation givenFullOperation() {
        return new FullOperation("/", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
    }

    private void removeTool(RegisteredTool tool) {
        try {
            if (this.mcpSyncServer != null) {
                this.mcpSyncServer.removeTool(tool.tool().name());
            }
        } catch (McpError ignored) {
        }

        try {
            if (this.mcpStatelessSyncServer != null) {
                this.mcpStatelessSyncServer.removeTool(tool.tool().name());
            }
        } catch (McpError ignored) {
        }
    }
}
