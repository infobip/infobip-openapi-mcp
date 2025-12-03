package com.infobip.openapi.mcp.enricher;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.util.XFFCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * API request enricher that adds the X-Forwarded-For header to downstream API requests.
 * <p>
 * This enricher ensures proper tracking of the original client IP address through the MCP server
 * by calculating and forwarding the X-Forwarded-For header chain. The chain includes any existing
 * X-Forwarded-For values from the original request, appended with the client's remote address.
 * </p>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>Calculates the X-Forwarded-For header value by chaining existing XFF with client IP</li>
 *   <li>If no HTTP request is available, no header is added</li>
 *   <li>Empty or blank values are not forwarded</li>
 *   <li>Thread-safe and stateless</li>
 * </ul>
 *
 * @see ApiRequestEnricher
 * @see McpRequestContext
 */
public class XForwardedForEnricher implements ApiRequestEnricher {

    public static final int ORDER = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(XForwardedForEnricher.class);
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final XFFCalculator xffCalculator;

    public XForwardedForEnricher(XFFCalculator xffCalculator) {
        this.xffCalculator = xffCalculator;
    }

    @Override
    public RestClient.RequestHeadersSpec<?> enrich(RestClient.RequestHeadersSpec<?> spec, McpRequestContext context) {
        var request = context.httpServletRequest();
        if (request == null) {
            LOGGER.trace("No HTTP request available for calculating X-Forwarded-For header");
            return spec;
        }

        var xForwardedForHeader = xffCalculator.calculateXFF(request);
        if (xForwardedForHeader != null && !xForwardedForHeader.isBlank()) {
            spec.header(X_FORWARDED_FOR_HEADER, xForwardedForHeader);
            LOGGER.debug("Added X-Forwarded-For header to downstream call: {}", xForwardedForHeader);
        }
        return spec;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
