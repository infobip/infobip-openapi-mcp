package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.AUTHORIZATION_ENDPOINT;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.ISSUER;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.MTLS_ENDPOINT_ALIASES;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.PUSHED_AUTHORIZATION_REQUEST_ENDPOINT;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.REGISTRATION_ENDPOINT;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.REQUIRE_PUSHED_AUTHORIZATION_REQUESTS;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.SCOPES_SUPPORTED;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.TOKEN_ENDPOINT;
import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.auth.web.AuthorizationServerProxy;
import com.infobip.openapi.mcp.auth.web.Spec;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional({OAuthEnabledCondition.class, McpServerStdioDisabledCondition.class})
public class OAuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthController.class);

    private final RestClient restClient;
    private final OAuthProperties oAuthProperties;
    private final ObjectMapper objectMapper;
    private final ErrorModelWriter errorModelWriter;
    private final Optional<ScopeDiscoveryService> scopeDiscoveryServiceOptional;
    private final ApiRequestEnricherChain enricherChain;
    private final McpRequestContextFactory contextFactory;
    private final XForwardedHostCalculator xForwardedHostCalculator;
    private final McpServerProperties mcpServerProperties;

    /**
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9728.txt">RFC 9728</a>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProtectedResource(
            String resource,
            String resource_name,
            Collection<String> authorization_servers,
            Collection<String> bearer_methods_supported,
            Collection<String> scopes_supported) {
        public ProtectedResource(String resource, String mcpServerName, URI oauthBaseUrl, Collection<String> scopes) {
            this(resource, mcpServerName, List.of(oauthBaseUrl.toString()), List.of("access_token"), scopes);
        }
    }

    public OAuthController(
            @Qualifier(OAUTH_REST_CLIENT_QUALIFIER) RestClient restClient,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryServiceOptional,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            XForwardedHostCalculator xForwardedHostCalculator,
            McpServerProperties mcpServerProperties) {
        this.restClient = restClient;
        this.oAuthProperties = oAuthProperties;
        this.objectMapper = objectMapper;
        this.errorModelWriter = errorModelWriter;
        this.scopeDiscoveryServiceOptional = scopeDiscoveryServiceOptional;
        this.enricherChain = enricherChain;
        this.contextFactory = contextFactory;
        this.xForwardedHostCalculator = xForwardedHostCalculator;
        this.mcpServerProperties = mcpServerProperties;
    }

    /**
     * Also mapped with a path suffix, so that the metadata can be discovered with path insertion (RFC 8414 section
     * 3.1) when the MCP server itself is advertised as the authorization server.
     */
    @GetMapping(
            path = {
                Spec.WellKnownPaths.OAUTH_AUTHORIZATION_SERVER,
                Spec.WellKnownPaths.OAUTH_AUTHORIZATION_SERVER + "/**"
            })
    public ResponseEntity<String> getOauthAuthorizationServer(HttpServletRequest request) {
        return proxy(Spec.WellKnownPaths.OAUTH_AUTHORIZATION_SERVER, request);
    }

    @GetMapping(path = {Spec.WellKnownPaths.OPENID_CONFIGURATION, Spec.WellKnownPaths.OPENID_CONFIGURATION + "/**"})
    public ResponseEntity<String> getOpenidConfiguration(HttpServletRequest request) {
        return proxy(Spec.WellKnownPaths.OPENID_CONFIGURATION, request);
    }

    @GetMapping(path = Spec.WellKnownPaths.OAUTH_PROTECTED_RESOURCE, produces = "application/json;charset=UTF-8")
    public ProtectedResource getProtectedResource(HttpServletRequest request) {
        var resource = xForwardedHostCalculator.hostWithRootPathBuilder(request).toUriString();
        // With the registration proxy, the MCP server acts as the authorization server so that clients discover the
        // proxied endpoints instead of the ones from the authorization server. The proxied authorization server
        // metadata advertises the same URL as its issuer.
        var authorizationServer = oAuthProperties.proxyEnabled() ? URI.create(resource) : oAuthProperties.url();
        return new ProtectedResource(
                resource,
                mcpServerProperties.getName(),
                authorizationServer,
                scopeDiscoveryServiceOptional
                        .map(ScopeDiscoveryService::getDiscoveredScopes)
                        .filter(scopes -> !scopes.isEmpty())
                        .orElse(null));
    }

    private ResponseEntity<String> proxy(String path, HttpServletRequest request) {
        var responseContentType = new MediaType(APPLICATION_JSON.getType(), APPLICATION_JSON.getSubtype(), UTF_8);
        var context = contextFactory.forServletFilter(request);
        var spec = restClient.get().uri(getWellKnownUri(path));

        spec = enricherChain.enrich(spec, context);

        try {
            var upstreamResponse = spec.retrieve().toEntity(String.class);
            if (!upstreamResponse.getStatusCode().is2xxSuccessful()) {
                LOGGER.error(
                        "Authorization server responded with status {} to well-known endpoint: {}",
                        upstreamResponse.getStatusCode(),
                        path);
                return internalServerError(responseContentType);
            }
            var jsonResponse = upstreamResponse.getBody();
            var proxyEnabled = oAuthProperties.proxyEnabled();

            if (scopeDiscoveryServiceOptional.isEmpty() && !proxyEnabled) {
                return ResponseEntity.ok().contentType(responseContentType).body(jsonResponse);
            }

            var json = (ObjectNode) objectMapper.readTree(jsonResponse);
            scopeDiscoveryServiceOptional.ifPresent(
                    service -> json.putPOJO(SCOPES_SUPPORTED, service.getDiscoveredScopes()));
            if (proxyEnabled) {
                rewriteForRegistrationProxy(json, request);
            }
            return ResponseEntity.ok().contentType(responseContentType).body(json.toString());
        } catch (JacksonException | RestClientException e) {
            LOGGER.error("Error proxying well-known endpoint: {}", path, e);
            return internalServerError(responseContentType);
        }
    }

    private ResponseEntity<String> internalServerError(MediaType responseContentType) {
        var errorResponse = errorModelWriter.writeErrorModelAsJson(HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.internalServerError()
                .contentType(responseContentType)
                .body(errorResponse);
    }

    /**
     * Presents the MCP server as the authorization server whose metadata is being served.
     * <p>
     * The issuer must be identical to the authorization server URL advertised in the protected resource metadata
     * (RFC 8414 section 3.3), otherwise clients reject the metadata. Registration, authorization and token endpoints
     * point to the {@link AuthorizationServerProxy}, which makes sure that authorization responses carry the same
     * issuer (RFC 9207). Pushed authorization requests are not advertised, since they would bypass the proxy. All other
     * endpoints keep pointing to the authorization server.
     * </p>
     */
    private void rewriteForRegistrationProxy(ObjectNode json, HttpServletRequest request) {
        var issuer = xForwardedHostCalculator.hostWithRootPathBuilder(request).toUriString();
        var proxiedEndpoints = Map.of(
                REGISTRATION_ENDPOINT, issuer + Spec.Paths.REGISTER,
                AUTHORIZATION_ENDPOINT, issuer + Spec.Paths.AUTHORIZE,
                TOKEN_ENDPOINT, issuer + Spec.Paths.TOKEN);

        json.put(ISSUER, issuer);
        json.remove(PUSHED_AUTHORIZATION_REQUEST_ENDPOINT);
        json.remove(REQUIRE_PUSHED_AUTHORIZATION_REQUESTS);
        var mtlsEndpointAliases = json.get(MTLS_ENDPOINT_ALIASES) instanceof ObjectNode objectNode ? objectNode : null;
        if (mtlsEndpointAliases != null) {
            mtlsEndpointAliases.remove(PUSHED_AUTHORIZATION_REQUEST_ENDPOINT);
        }
        proxiedEndpoints.forEach((name, endpoint) -> {
            json.put(name, endpoint);
            if (mtlsEndpointAliases != null && mtlsEndpointAliases.has(name)) {
                mtlsEndpointAliases.put(name, endpoint);
            }
        });
    }

    private URI getWellKnownUri(String path) {
        return UriComponentsBuilder.fromUri(oAuthProperties.url())
                .path(path)
                .build()
                .toUri();
    }
}
