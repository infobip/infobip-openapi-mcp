package com.infobip.openapi.mcp.auth.web;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.enricher.XForwardedForEnricher;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proxies OAuth dynamic client registration requests (RFC 7591) to the authorization server.
 * <p>
 * Some MCP clients register OAuth clients without the scopes the MCP server requires, which results in tokens that
 * cannot be used with the server. Before forwarding, this proxy replaces the {@code scope} field of the client
 * metadata with the scopes required by the MCP server (the same scopes advertised in {@code scopes_supported} and in
 * the {@code WWW-Authenticate} header). The authorization server response is returned to the client as is.
 * </p>
 * <p>
 * The upstream registration endpoint is taken from configuration, or read from the {@code registration_endpoint} of
 * the authorization server's OpenID configuration and cached.
 * </p>
 */
public class ClientRegistrationProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRegistrationProxy.class);

    private static final String OPENID_CONFIGURATION_PATH = "/.well-known/openid-configuration";
    private static final String REGISTRATION_ENDPOINT = "registration_endpoint";
    private static final String SCOPE = "scope";
    private static final List<String> FORWARDED_REQUEST_HEADERS =
            List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT);
    private static final List<String> FORWARDED_RESPONSE_HEADERS =
            List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL, HttpHeaders.PRAGMA);

    private final RestClient registrationRestClient;
    private final RestClient metadataRestClient;
    private final OAuthProperties oAuthProperties;
    private final ObjectMapper objectMapper;
    private final ErrorModelWriter errorModelWriter;
    private final Optional<ScopeDiscoveryService> scopeDiscoveryService;
    private final XForwardedForEnricher xForwardedForEnricher;
    private final McpRequestContextFactory contextFactory;

    private volatile @Nullable URI upstreamUrl;

    /**
     * @param registrationRestClient client used to forward registration requests; must not throw on non-2xx responses
     * @param metadataRestClient     client used to read the authorization server metadata
     */
    public ClientRegistrationProxy(
            RestClient registrationRestClient,
            RestClient metadataRestClient,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            XForwardedForEnricher xForwardedForEnricher,
            McpRequestContextFactory contextFactory) {
        this.registrationRestClient = registrationRestClient;
        this.metadataRestClient = metadataRestClient;
        this.oAuthProperties = oAuthProperties;
        this.objectMapper = objectMapper;
        this.errorModelWriter = errorModelWriter;
        this.scopeDiscoveryService = scopeDiscoveryService;
        this.xForwardedForEnricher = xForwardedForEnricher;
        this.contextFactory = contextFactory;
        this.upstreamUrl = oAuthProperties.registrationProxy().upstreamUrl();
        if (scopeDiscoveryService.isEmpty()) {
            LOGGER.warn("OAuth scope discovery is disabled, client registration requests will be proxied without "
                    + "changing the requested scopes.");
        }
    }

    /**
     * Forwards the client registration request to the authorization server.
     *
     * @param request the incoming client registration request
     * @param body    the raw request body
     * @return the authorization server response, or an error response if the request could not be forwarded
     */
    public ResponseEntity<byte[]> register(HttpServletRequest request, byte @Nullable [] body) {
        ObjectNode clientMetadata;
        try {
            if (body == null || body.length == 0) {
                return invalidClientMetadata("Request body must be a JSON object.");
            }
            if (!(objectMapper.readTree(body) instanceof ObjectNode objectNode)) {
                return invalidClientMetadata("Request body must be a JSON object.");
            }
            clientMetadata = objectNode;
        } catch (JacksonException e) {
            return invalidClientMetadata("Request body must be a JSON object.");
        }

        var requestedScope = clientMetadata.path(SCOPE).asString(null);
        scopeDiscoveryService.map(ScopeDiscoveryService::getDiscoveredScopes).ifPresent(scopes -> {
            if (!scopes.isEmpty()) {
                clientMetadata.put(SCOPE, String.join(" ", scopes));
            }
        });
        LOGGER.info(
                "Proxying OAuth client registration for client '{}', requested scope '{}', forwarded scope '{}'.",
                clientMetadata.path("client_name").asString(null),
                requestedScope,
                clientMetadata.path(SCOPE).asString(null));

        try {
            var spec = registrationRestClient
                    .post()
                    .uri(resolveUpstreamUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsBytes(clientMetadata));
            for (var headerName : FORWARDED_REQUEST_HEADERS) {
                var headerValue = request.getHeader(headerName);
                if (headerValue != null) {
                    spec.header(headerName, headerValue);
                }
            }
            xForwardedForEnricher.enrich(spec, contextFactory.forServletFilter(request));

            var upstreamResponse = spec.retrieve().toEntity(byte[].class);
            var responseHeaders = new HttpHeaders();
            for (var headerName : FORWARDED_RESPONSE_HEADERS) {
                var headerValues = upstreamResponse.getHeaders().get(headerName);
                if (headerValues != null) {
                    responseHeaders.addAll(headerName, headerValues);
                }
            }
            return ResponseEntity.status(upstreamResponse.getStatusCode())
                    .headers(responseHeaders)
                    .body(upstreamResponse.getBody());
        } catch (RestClientException | JacksonException | IllegalStateException e) {
            LOGGER.error("Error proxying OAuth client registration request.", e);
            upstreamUrl = oAuthProperties.registrationProxy().upstreamUrl();
            var errorResponse = errorModelWriter.writeErrorModelAsJson(HttpStatus.BAD_GATEWAY, request, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse.getBytes(StandardCharsets.UTF_8));
        }
    }

    private URI resolveUpstreamUrl() {
        var cachedUpstreamUrl = upstreamUrl;
        if (cachedUpstreamUrl != null) {
            return cachedUpstreamUrl;
        }

        var openIdConfigurationUri = UriComponentsBuilder.fromUri(oAuthProperties.url())
                .path(OPENID_CONFIGURATION_PATH)
                .build()
                .toUri();
        var metadata =
                metadataRestClient.get().uri(openIdConfigurationUri).retrieve().body(String.class);
        var registrationEndpoint = metadata == null
                ? null
                : objectMapper.readTree(metadata).path(REGISTRATION_ENDPOINT).asString(null);
        if (registrationEndpoint == null || registrationEndpoint.isBlank()) {
            throw new IllegalStateException(
                    "Authorization server metadata at " + openIdConfigurationUri + " has no " + REGISTRATION_ENDPOINT);
        }

        var resolvedUpstreamUrl = URI.create(registrationEndpoint);
        upstreamUrl = resolvedUpstreamUrl;
        return resolvedUpstreamUrl;
    }

    private ResponseEntity<byte[]> invalidClientMetadata(String description) {
        var error = objectMapper
                .createObjectNode()
                .put("error", "invalid_client_metadata")
                .put("error_description", description);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsBytes(error));
    }
}
