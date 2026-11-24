package com.infobip.openapi.mcp.auth.scope;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.config.ApiBaseUrlProvider;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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

    private static final String INSUFFICIENT_SCOPE_ERROR = "insufficient_scope";
    private static final String CHALLENGE_ERROR_DESCRIPTION = "You need to login to continue";

    private final OAuthProperties oAuthProperties;
    private final Optional<ScopeDiscoveryService> scopeDiscoveryService;
    private final XForwardedHostCalculator xForwardedHostCalculator;
    private final ApiBaseUrlProvider apiBaseUrlProvider;

    public WwwAuthenticateProvider(
            OAuthProperties oAuthProperties,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            XForwardedHostCalculator xForwardedHostCalculator,
            ApiBaseUrlProvider apiBaseUrlProvider) {
        this.oAuthProperties = oAuthProperties;
        this.scopeDiscoveryService = scopeDiscoveryService;
        this.xForwardedHostCalculator = xForwardedHostCalculator;
        this.apiBaseUrlProvider = apiBaseUrlProvider;
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
        return buildWwwAuthenticateHeader(request, null, null);
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
        return buildWwwAuthenticateHeader(request, INSUFFICIENT_SCOPE_ERROR, null);
    }

    /**
     * Builds the authentication challenge for in-band signaling via
     * {@code _meta["mcp/www_authenticate"]} on a tool call result.
     * <p>
     * Hosts with mixed authentication (ChatGPT connectors) read authorization hints from the MCP
     * layer instead of the transport plane, and their documented value format requires both an
     * {@code error} and an {@code error_description} parameter. The challenge therefore always
     * reports {@code insufficient_scope} with a sign-in instruction, regardless of whether the
     * backing API answered {@code 401} or {@code 403} — both mean "login is required to continue"
     * for an anonymous caller.
     * </p>
     *
     * @param request the HTTP servlet request used to determine the resource metadata URL
     * @return the formatted challenge parameter string
     */
    public String buildWwwAuthenticateChallenge(HttpServletRequest request) {
        return buildWwwAuthenticateHeader(request, INSUFFICIENT_SCOPE_ERROR, CHALLENGE_ERROR_DESCRIPTION);
    }

    private String buildWwwAuthenticateHeader(
            HttpServletRequest request, @Nullable String error, @Nullable String errorDescription) {
        var wwwAuthenticateProperties = oAuthProperties.wwwAuthenticate();

        var builder =
                switch (wwwAuthenticateProperties.urlSource()) {
                    case API_BASE_URL -> UriComponentsBuilder.fromUri(apiBaseUrlProvider.get());
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

        if (error != null) {
            stringBuilder.append(", error=");
            stringBuilder.append("\"");
            stringBuilder.append(error);
            stringBuilder.append("\"");
        }

        if (errorDescription != null) {
            stringBuilder.append(", error_description=");
            stringBuilder.append("\"");
            stringBuilder.append(errorDescription);
            stringBuilder.append("\"");
        }

        return stringBuilder.toString();
    }
}
