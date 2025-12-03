package com.infobip.openapi.mcp.enricher;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Enricher that adds User-Agent header to downstream API requests.
 * <p>
 * This enricher applies a consistent User-Agent header to all outgoing HTTP requests,
 * including both authentication validation requests and downstream API calls. The User-Agent
 * value is configured via {@code infobip.openapi.mcp.userAgent} property.
 * </p>
 *
 * <h3>Configuration:</h3>
 * <p>
 * The User-Agent string is read from {@link OpenApiMcpProperties#userAgent()}. If not
 * configured or blank, no User-Agent header will be added.
 * </p>
 *
 * <h3>Usage:</h3>
 * <p>
 * This enricher is automatically registered and applied to all downstream HTTP calls
 * through the {@link ApiRequestEnricher} mechanism. It ensures consistent User-Agent
 * identification across all API interactions.
 * </p>
 *
 * @see ApiRequestEnricher
 * @see OpenApiMcpProperties
 */
public class UserAgentEnricher implements ApiRequestEnricher {

    public static final int ORDER = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAgentEnricher.class);

    private final String userAgent;

    public UserAgentEnricher(OpenApiMcpProperties properties) {
        this.userAgent = properties.userAgent();
    }

    @Override
    public RestClient.RequestHeadersSpec<?> enrich(RestClient.RequestHeadersSpec<?> spec, McpRequestContext context) {
        if (!userAgent.isBlank()) {
            spec = spec.header(HttpHeaders.USER_AGENT, userAgent);
            LOGGER.debug("Added User-Agent header to downstream call: {}", userAgent);
        }
        return spec;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
