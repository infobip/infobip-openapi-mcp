package com.infobip.openapi.mcp.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.util.ForwardedHeaderUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class XForwardedHostCalculator {

    private final @Nullable McpServerStreamableHttpProperties streamableProps;
    private final @Nullable McpServerSseProperties sseProps;

    public XForwardedHostCalculator(
            Optional<McpServerStreamableHttpProperties> streamableProps, Optional<McpServerSseProperties> sseProps) {
        this.streamableProps = streamableProps.orElse(null);
        this.sseProps = sseProps.orElse(null);
    }

    public @NonNull UriComponentsBuilder hostWithRootPathBuilder(HttpServletRequest request) {
        return hostBuilder(request).path(rootPath());
    }

    /**
     * Returns the root path of the MCP server, which is the SSE endpoint or the MCP endpoint, depending on the
     * transport, or an empty string when neither is configured.
     */
    public @NonNull String rootPath() {
        if (sseProps != null) {
            return sseProps.getSseEndpoint();
        } else if (streamableProps != null) {
            return streamableProps.getMcpEndpoint();
        }
        return "";
    }

    public @NonNull UriComponentsBuilder hostBuilder(HttpServletRequest request) {
        var springReq = new ServletServerHttpRequest(request);
        var requestUri = springReq.getURI();
        try {
            var rootUrl = new URI(requestUri.getScheme(), requestUri.getAuthority(), null, null, null);
            return ForwardedHeaderUtils.adaptFromForwardedHeaders(rootUrl, springReq.getHeaders());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
