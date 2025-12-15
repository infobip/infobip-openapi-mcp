package com.infobip.openapi.mcp.auth.scope;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;

public class WwwAuthenticateProvider {

    private final OAuthProperties oAuthProperties;
    private final Optional<ScopeDiscoveryService> scopeDiscoveryService;
    private final OpenApiMcpProperties openApiMcpProperties;
    private final XForwardedHostCalculator xForwardedHostCalculator;

    public WwwAuthenticateProvider(
            OAuthProperties oAuthProperties,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            OpenApiMcpProperties openApiMcpProperties,
            XForwardedHostCalculator xForwardedHostCalculator) {
        this.oAuthProperties = oAuthProperties;
        this.scopeDiscoveryService = scopeDiscoveryService;
        this.openApiMcpProperties = openApiMcpProperties;
        this.xForwardedHostCalculator = xForwardedHostCalculator;
    }

    public String buildWwwAuthenticateHeader(HttpServletRequest request) {
        return buildWwwAuthenticateHeader(request, false);
    }

    public String buildWwwAuthenticateHeaderWithScopeError(HttpServletRequest request) {
        return buildWwwAuthenticateHeader(request, true);
    }

    private String buildWwwAuthenticateHeader(HttpServletRequest request, boolean isInsufficientScope) {
        var wwwAuthenticateProperties = oAuthProperties.wwwAuthenticate();

        var builder =
                switch (wwwAuthenticateProperties.urlSource()) {
                    case API_BASE_URL -> UriComponentsBuilder.fromUri(openApiMcpProperties.apiBaseUrl());
                    case X_FORWARDED_HOST ->
                        wwwAuthenticateProperties.includeMcpEndpoint()
                                ? xForwardedHostCalculator.hostWithRootPathBuilder(request)
                                : xForwardedHostCalculator.hostBuilder(request);
                };
        var wellKnownUrl = builder.path(OAuthProperties.WELL_KNOWN_PATH).build().toUriString();

        var stringBuilder = new StringBuilder();
        stringBuilder.append("Bearer ");
        stringBuilder.append("resource_metadata=");
        stringBuilder.append("\"").append(wellKnownUrl).append("\"");

        scopeDiscoveryService.ifPresent(service -> {
            var discoveredScopes = service.getDiscoveredScopes();
            if (!discoveredScopes.isEmpty()) {
                stringBuilder.append(", scope=");
                stringBuilder
                        .append("\"")
                        .append(String.join(" ", discoveredScopes))
                        .append("\"");
            }
        });

        if (isInsufficientScope) {
            stringBuilder.append(", error=");
            stringBuilder.append("\"");
            stringBuilder.append("insufficient_scope");
            stringBuilder.append("\"");
        }

        return stringBuilder.toString();
    }
}
