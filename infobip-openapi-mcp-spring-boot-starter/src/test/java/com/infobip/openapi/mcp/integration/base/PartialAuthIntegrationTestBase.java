package com.infobip.openapi.mcp.integration.base;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Public extension point for the partial-authentication concrete integration tests, mirroring the convention that
 * concrete tests live in {@code com.infobip.openapi.mcp.integration} while {@link IntegrationTestBase}
 * stays package-private.
 */
public abstract class PartialAuthIntegrationTestBase extends IntegrationTestBase {

    private static final HttpClient RAW_HTTP_CLIENT = HttpClient.newBuilder().build();

    /**
     * Posts a raw JSON-RPC message to the MCP endpoint with a chosen {@code User-Agent}, bypassing the MCP
     * client SDK. Needed to inspect the HTTP response plane (status and headers) the SDK hides, which is
     * exactly what the partial-authentication challenge planes are made of.
     */
    protected HttpResponse<String> postJsonRpcAs(String userAgent, String jsonRpcBody) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + mcpServerStreamableHttpProperties.getMcpEndpoint()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-06-18")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRpcBody))
                .build();
        try {
            return RAW_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing the MCP endpoint", e);
        }
    }

    protected String toolsCallBody(String toolName) {
        return """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"%s","arguments":{}}}\
                """.formatted(toolName);
    }
}
