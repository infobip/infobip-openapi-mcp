package com.infobip.openapi.mcp.enricher;

import com.infobip.openapi.mcp.McpRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * API request enricher that forwards the X-Forwarded-Host header to downstream API requests.
 * <p>
 * This enricher preserves the original host information from the incoming request by forwarding
 * the {@code X-Forwarded-Host} header to downstream API calls. Following standard HTTP proxy behavior,
 * it uses a priority-based approach to determine the host value. This is essential for:
 * </p>
 * <ul>
 *   <li>Maintaining original host information through proxy chains</li>
 *   <li>Supporting virtual hosting scenarios where the downstream service needs to know the original host</li>
 *   <li>Enabling proper request tracing and debugging across multiple proxies</li>
 *   <li>Allowing downstream services to generate correct URLs and redirects</li>
 * </ul>
 *
 * <h3>Priority Logic:</h3>
 * <p>
 * The enricher follows standard proxy behavior with the following priority order:
 * </p>
 * <ol>
 *   <li><strong>X-Forwarded-Host header</strong>: If present, use it (preserves original host through proxy chain)</li>
 *   <li><strong>Host header</strong>: If X-Forwarded-Host is absent, use Host (MCP server is first proxy)</li>
 *   <li><strong>No header</strong>: If neither exists, no header is added to downstream call</li>
 * </ol>
 *
 * <h3>Usage Examples:</h3>
 * <dl>
 *   <dt><strong>Scenario 1: MCP Server as First Proxy</strong></dt>
 *   <dd>
 *     <pre>
 *     Client → MCP Server → Downstream API
 *
 *     Incoming request:
 *       Host: api.example.com
 *       (no X-Forwarded-Host)
 *
 *     Outgoing to downstream:
 *       X-Forwarded-Host: api.example.com
 *     </pre>
 *   </dd>
 *   <dt><strong>Scenario 2: MCP Server in Proxy Chain</strong></dt>
 *   <dd>
 *     <pre>
 *     Client → Proxy1 → MCP Server → Downstream API
 *
 *     Incoming request:
 *       Host: mcp-server.internal
 *       X-Forwarded-Host: api.example.com
 *
 *     Outgoing to downstream:
 *       X-Forwarded-Host: api.example.com
 *     </pre>
 *   </dd>
 * </dl>
 *
 * <h3>Why X-Forwarded-Host Takes Precedence:</h3>
 * <p>
 * The {@code Host} header gets rewritten at each proxy hop to point to the next destination,
 * while {@code X-Forwarded-Host} preserves the original client host throughout the proxy chain.
 * When both headers are present, {@code X-Forwarded-Host} contains the source of truth.
 * </p>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>Thread-safe and stateless</li>
 *   <li>No validation or modification of header values is performed</li>
 *   <li>Empty or blank values are treated as absent</li>
 *   <li>Supports all valid host formats (domains, IPs, ports, IPv6)</li>
 * </ul>
 *
 * <h3>Header Format:</h3>
 * <p>
 * Common formats for both {@code Host} and {@code X-Forwarded-Host} headers:
 * </p>
 * <ul>
 *   <li>{@code example.com}</li>
 *   <li>{@code api.example.com:8080}</li>
 *   <li>{@code 192.168.1.100}</li>
 *   <li>{@code [2001:db8::1]}</li>
 * </ul>
 *
 * <h3>Standards Reference:</h3>
 * <p>
 * This enricher follows the conventions established in RFC 7239 (Forwarded HTTP Extension)
 * and mimics the behavior of standard HTTP proxies like nginx, Apache, and HAProxy.
 * </p>
 *
 * @see ApiRequestEnricher
 * @see McpRequestContext
 */
public class XForwardedHostEnricher implements ApiRequestEnricher {

    public static final int ORDER = 200;

    private static final Logger LOGGER = LoggerFactory.getLogger(XForwardedHostEnricher.class);
    private static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";

    @Override
    public RestClient.RequestHeadersSpec<?> enrich(RestClient.RequestHeadersSpec<?> spec, McpRequestContext context) {
        var request = context.httpServletRequest();
        if (request == null) {
            LOGGER.trace("No HTTP request available for X-Forwarded-Host processing");
            return spec;
        }

        var hostValue = resolveHostValue(request);
        if (hostValue != null && !hostValue.isBlank()) {
            spec = spec.header(X_FORWARDED_HOST_HEADER, hostValue);
            LOGGER.debug("Added X-Forwarded-Host header to downstream call: {}", hostValue);
        }

        return spec;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Resolves the host value to forward in X-Forwarded-Host header.
     * <p>
     * Priority order:
     * <ol>
     *   <li>Use X-Forwarded-Host if present (preserves original through proxy chain)</li>
     *   <li>Fall back to Host header (first proxy in chain)</li>
     *   <li>Return null if neither exists</li>
     * </ol>
     *
     * @param request the HTTP servlet request
     * @return the host value to forward, or null if not available
     */
    private @Nullable String resolveHostValue(HttpServletRequest request) {
        // Priority 1: X-Forwarded-Host (original host through proxy chain)
        var xForwardedHost = request.getHeader(X_FORWARDED_HOST_HEADER);
        if (xForwardedHost != null && !xForwardedHost.isBlank()) {
            return xForwardedHost;
        }

        // Priority 2: Host header (first proxy in chain)
        var host = request.getHeader(HttpHeaders.HOST);
        if (host != null && !host.isBlank()) {
            return host;
        }

        // Neither header present
        return null;
    }
}
