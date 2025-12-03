package com.infobip.openapi.mcp.auth.web;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authentication filter that validates credentials with an external auth service.
 * <p>
 * This filter intercepts requests and validates the Authorization header by making
 * a request to an external authentication endpoint. If validation succeeds, the
 * request continues to the target endpoint. If validation fails, an error response
 * is returned.
 * </p>
 *
 * <h3>Authorization Handling:</h3>
 * <p>
 * Authorization header forwarding is <b>intentionally explicit</b> in this filter
 * rather than delegated to enrichers. This design decision ensures:
 * </p>
 * <ul>
 *   <li>Security logic is obvious and easy to audit</li>
 *   <li>Auth is the subject of validation, not just metadata</li>
 *   <li>Auth failures are immediate and clear</li>
 * </ul>
 *
 * <h3>Enrichers Applied:</h3>
 * <p>
 * After the Authorization header is explicitly set, enrichers are applied.
 * <p>
 * This allows the auth service to receive the same context as
 * downstream API calls.
 * </p>
 *
 * @see ApiRequestEnricherChain
 * @see McpRequestContext
 */
public class InitialAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAuthenticationFilter.class);

    // Hop-by-hop headers that should not be copied in proxy mode
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade");

    private final RestClient restClient;
    private final AuthProperties authProperties;
    private final Optional<OAuthProperties> oAuthProperties;
    private final OpenApiMcpProperties openApiMcpProperties;
    private final ErrorModelWriter errorModelWriter;
    private final ApiRequestEnricherChain enricherChain;
    private final McpRequestContextFactory contextFactory;
    private final Optional<ScopeDiscoveryService> scopeDiscoveryService;
    private final XForwardedHostCalculator xForwardedHostCalculator;

    public InitialAuthenticationFilter(
            RestClient restClient,
            AuthProperties authProperties,
            Optional<OAuthProperties> oAuthProperties,
            OpenApiMcpProperties openApiMcpProperties,
            ErrorModelWriter errorModelWriter,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            XForwardedHostCalculator xForwardedHostCalculator) {
        this.authProperties = authProperties;
        this.restClient = restClient;
        this.oAuthProperties = oAuthProperties;
        this.openApiMcpProperties = openApiMcpProperties;
        this.errorModelWriter = errorModelWriter;
        this.enricherChain = enricherChain;
        this.contextFactory = contextFactory;
        this.scopeDiscoveryService = scopeDiscoveryService;
        this.xForwardedHostCalculator = xForwardedHostCalculator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) {
            writeUnauthorizedResponse(request, response);
            return;
        }

        try {
            var context = contextFactory.forServletFilter(request);
            var externalResponse = makeExternalAuthorizationRequest(authHeader, context);

            if (externalResponse.getStatusCode().is2xxSuccessful()) {
                filterChain.doFilter(request, response);
            } else {
                writeErrorResponse(request, response, externalResponse, null);
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Failed to validate credentials: {}", exception.getMessage(), exception);
            writeErrorResponse(request, response, null, exception);
        }
    }

    private ResponseEntity<byte[]> makeExternalAuthorizationRequest(String authHeader, McpRequestContext context) {
        var spec = restClient.get().uri(authProperties.authUrl());

        if (authHeader != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, authHeader);
            LOGGER.debug("Forwarding Authorization header to auth validation endpoint");
        }

        spec = enricherChain.enrich(spec, context);

        return spec.retrieve().toEntity(byte[].class);
    }

    private void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ResponseEntity<byte[]> externalResponse,
            Exception exception)
            throws IOException {
        if (externalResponse != null) {
            response.setStatus(externalResponse.getStatusCode().value());

            if (response.getStatus() == HttpStatus.UNAUTHORIZED.value() && isOAuthEnabled()) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, buildWwwAuthenticateHeader(request));
            }

            if (authProperties.overrideExternalResponse()) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter()
                        .write( // NOSONAR safe content, XSS not possible
                                errorModelWriter.writeErrorModelAsJson(
                                        externalResponse.getStatusCode(), request, exception));
            } else {
                copyResponseHeaders(externalResponse.getHeaders(), response);
                var body = externalResponse.getBody();
                if (body != null && body.length > 0) {
                    response.getOutputStream().write(body);
                }
            }
        } else {
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter()
                    .write( // NOSONAR safe content, XSS not possible
                            errorModelWriter.writeErrorModelAsJson(
                                    HttpStatus.INTERNAL_SERVER_ERROR, request, exception));
        }
    }

    private void copyResponseHeaders(HttpHeaders sourceHeaders, HttpServletResponse targetResponse) {
        sourceHeaders.forEach((headerName, headerValues) -> {
            if (!HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                headerValues.forEach(headerValue -> targetResponse.addHeader(headerName, headerValue));
            }
        });
    }

    private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        if (isOAuthEnabled()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, buildWwwAuthenticateHeader(request));
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write( // NOSONAR safe content, XSS not possible
                        errorModelWriter.writeErrorModelAsJson(HttpStatus.UNAUTHORIZED, request, null));
    }

    private String buildWwwAuthenticateHeader(HttpServletRequest request) {
        var wwwAuthenticateProperties = this.oAuthProperties.get().wwwAuthenticate();
        var scopeDiscoveryService = this.scopeDiscoveryService.get();

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
        stringBuilder.append("Bearer resource_metadata=");
        stringBuilder.append("\"").append(wellKnownUrl).append("\"");

        var discoveredScopes = scopeDiscoveryService.getDiscoveredScopes();
        if (!discoveredScopes.isEmpty()) {
            stringBuilder.append(", scope=");
            stringBuilder
                    .append("\"")
                    .append(String.join(" ", discoveredScopes))
                    .append("\"");
        }

        return stringBuilder.toString();
    }

    private boolean isOAuthEnabled() {
        return oAuthProperties.map(OAuthProperties::enabled).orElse(false);
    }
}
