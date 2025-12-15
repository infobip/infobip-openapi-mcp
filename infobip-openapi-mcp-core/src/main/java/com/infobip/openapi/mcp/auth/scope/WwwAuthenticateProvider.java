package com.infobip.openapi.mcp.auth.scope;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Provider for constructing WWW-Authenticate response headers according to RFC 6750.
 * <p>
 * This provider builds WWW-Authenticate headers that include OAuth 2.0 metadata such as
 * the resource metadata URL (pointing to the well-known OAuth configuration), discovered
 * scopes, and optional error information for insufficient scope scenarios.
 * </p>
 */
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

    /**
     * Builds a WWW-Authenticate header for Bearer token authentication.
     * <p>
     * The header includes the resource metadata URL pointing to the OAuth well-known
     * configuration endpoint and any discovered scopes if available.
     * </p>
     *
     * @param request the HTTP servlet request used to determine the resource metadata URL
     * @return the formatted WWW-Authenticate header value
     */
    public String buildWwwAuthenticateHeader(HttpServletRequest request) {
        return buildWwwAuthenticateHeader(request, false);
    }

    /**
     * Builds a WWW-Authenticate header with an insufficient_scope error.
     * <p>
     * This method is used when a request lacks the required scope to access a resource.
     * The header includes the resource metadata URL, any discovered scopes if available,
     * and an error attribute set to "insufficient_scope".
     * </p>
     *
     * @param request the HTTP servlet request used to determine the resource metadata URL
     * @return the formatted WWW-Authenticate header value with error information
     */
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
