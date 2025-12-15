package com.infobip.openapi.mcp.enricher;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponents;

/**
 * API request enricher that forwards host, protocol, and port information to downstream API requests.
 * <p>
 * This enricher preserves the original request information by setting three separate headers that
 * follow standard HTTP proxy behavior. It uses {@link XForwardedHostCalculator} to properly extract
 * these values from the incoming request, respecting X-Forwarded-* headers when present. This is essential for:
 * </p>
 * <ul>
 *   <li>Maintaining original host information through proxy chains</li>
 *   <li>Supporting virtual hosting scenarios where the downstream service needs to know the original host</li>
 *   <li>Enabling proper request tracing and debugging across multiple proxies</li>
 *   <li>Allowing downstream services to generate correct URLs and redirects</li>
 * </ul>
 *
 * <h3>Headers Set:</h3>
 * <p>
 * The enricher sets three separate headers for downstream API calls:
 * </p>
 * <ul>
 *   <li><strong>X-Forwarded-Host</strong>: The hostname without port (e.g., {@code api.example.com})</li>
 *   <li><strong>X-Forwarded-Proto</strong>: The protocol/scheme (e.g., {@code https} or {@code http})</li>
 *   <li><strong>X-Forwarded-Port</strong>: The port number, only if non-default (e.g., {@code 8080})</li>
 * </ul>
 *
 * <h3>Port Handling:</h3>
 * <p>
 * Default ports are not included in the X-Forwarded-Port header:
 * </p>
 * <ul>
 *   <li>Port 80 for HTTP - X-Forwarded-Port is not set</li>
 *   <li>Port 443 for HTTPS - X-Forwarded-Port is not set</li>
 *   <li>Non-default ports - X-Forwarded-Port is set with the actual port number</li>
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 * <dl>
 *   <dt><strong>Scenario 1: HTTPS Request with Default Port</strong></dt>
 *   <dd>
 *     <pre>
 *     Client → MCP Server → Downstream API
 *
 *     Incoming request:
 *       Host: api.example.com
 *       Scheme: HTTPS
 *
 *     Outgoing to downstream:
 *       X-Forwarded-Host: api.example.com
 *       X-Forwarded-Proto: https
 *       (X-Forwarded-Port not set - default port 443)
 *     </pre>
 *   </dd>
 *   <dt><strong>Scenario 2: HTTP Request with Non-Default Port</strong></dt>
 *   <dd>
 *     <pre>
 *     Client → MCP Server → Downstream API
 *
 *     Incoming request:
 *       Host: api.example.com:8080
 *       Scheme: HTTP
 *
 *     Outgoing to downstream:
 *       X-Forwarded-Host: api.example.com
 *       X-Forwarded-Proto: http
 *       X-Forwarded-Port: 8080
 *     </pre>
 *   </dd>
 *   <dt><strong>Scenario 3: MCP Server in Proxy Chain</strong></dt>
 *   <dd>
 *     <pre>
 *     Client → Proxy1 → MCP Server → Downstream API
 *
 *     Incoming request:
 *       Host: mcp-server.internal
 *       X-Forwarded-Host: api.example.com
 *       X-Forwarded-Proto: https
 *       X-Forwarded-Port: 9443
 *
 *     Outgoing to downstream:
 *       X-Forwarded-Host: api.example.com
 *       X-Forwarded-Proto: https
 *       X-Forwarded-Port: 9443
 *     </pre>
 *   </dd>
 * </dl>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>Thread-safe and stateless</li>
 *   <li>Uses {@link XForwardedHostCalculator} to extract host, port, and protocol with proper X-Forwarded-* header handling</li>
 *   <li>Empty or blank host values result in no headers being set</li>
 *   <li>Supports all valid host formats (domains, IPs, IPv6)</li>
 *   <li>Automatically handles default port detection (80 for HTTP, 443 for HTTPS)</li>
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
    private static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    private static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

    private final XForwardedHostCalculator xForwardedHostCalculator;

    public XForwardedHostEnricher(XForwardedHostCalculator xForwardedHostCalculator) {
        this.xForwardedHostCalculator = xForwardedHostCalculator;
    }

    @Override
    public RestClient.RequestHeadersSpec<?> enrich(RestClient.RequestHeadersSpec<?> spec, McpRequestContext context) {
        var request = context.httpServletRequest();
        if (request == null) {
            LOGGER.trace("No HTTP request available for X-Forwarded-Host processing");
            return spec;
        }

        var uriBuilder = xForwardedHostCalculator.hostBuilder(request);
        var uriComponents = uriBuilder.build();

        var host = uriComponents.getHost();
        if (host == null || host.isBlank()) {
            LOGGER.trace("No host available from request");
            return spec;
        }

        // Set X-Forwarded-Host
        spec = spec.header(X_FORWARDED_HOST_HEADER, host);

        // Set X-Forwarded-Proto
        var scheme = uriComponents.getScheme();
        if (scheme != null && !scheme.isBlank()) {
            spec = spec.header(X_FORWARDED_PROTO_HEADER, scheme);
        }

        // Set X-Forwarded-Port
        var port = uriComponents.getPort();
        var PORT_NOT_SET = -1;
        if (port != PORT_NOT_SET) {
            spec = spec.header(X_FORWARDED_PORT_HEADER, String.valueOf(port));
        }

        return spec;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
