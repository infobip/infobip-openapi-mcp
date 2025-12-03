package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.enricher.XForwardedForEnricher;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collection;
import java.util.List;
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
    private final XForwardedForEnricher xForwardedForEnricher;
    private final McpRequestContextFactory contextFactory;
    private final XForwardedHostCalculator xForwardedHostCalculator;
    private final McpServerProperties mcpServerProperties;

    public static final String OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH = "/.well-known/oauth-authorization-server";
    public static final String OPENID_CONFIGURATION_WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    public static final String OAUTH_PROTECTED_RESOURCE_WELL_KNOWN_PATH = "/.well-known/oauth-protected-resource";

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
            XForwardedForEnricher xForwardedForEnricher,
            McpRequestContextFactory contextFactory,
            XForwardedHostCalculator xForwardedHostCalculator,
            McpServerProperties mcpServerProperties) {
        this.restClient = restClient;
        this.oAuthProperties = oAuthProperties;
        this.objectMapper = objectMapper;
        this.errorModelWriter = errorModelWriter;
        this.scopeDiscoveryServiceOptional = scopeDiscoveryServiceOptional;
        this.xForwardedForEnricher = xForwardedForEnricher;
        this.contextFactory = contextFactory;
        this.xForwardedHostCalculator = xForwardedHostCalculator;
        this.mcpServerProperties = mcpServerProperties;
    }

    @GetMapping(path = OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH)
    public ResponseEntity<String> getOauthAuthorizationServer(HttpServletRequest request) {
        return proxy(OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH, request);
    }

    @GetMapping(path = OPENID_CONFIGURATION_WELL_KNOWN_PATH)
    public ResponseEntity<String> getOpenidConfiguration(HttpServletRequest request) {
        return proxy(OPENID_CONFIGURATION_WELL_KNOWN_PATH, request);
    }

    @GetMapping(path = OAUTH_PROTECTED_RESOURCE_WELL_KNOWN_PATH, produces = "application/json;charset=UTF-8")
    public ProtectedResource getProtectedResource(HttpServletRequest request) {
        return new ProtectedResource(
                xForwardedHostCalculator.hostWithRootPathBuilder(request).toUriString(),
                mcpServerProperties.getName(),
                oAuthProperties.url(),
                scopeDiscoveryServiceOptional
                        .map(ScopeDiscoveryService::getDiscoveredScopes)
                        .filter(scopes -> !scopes.isEmpty())
                        .orElse(null));
    }

    private ResponseEntity<String> proxy(String path, HttpServletRequest request) {
        var responseContentType = new MediaType(APPLICATION_JSON.getType(), APPLICATION_JSON.getSubtype(), UTF_8);
        var context = contextFactory.forServletFilter(request);
        var spec = restClient.get().uri(getWellKnownUri(path));

        spec = xForwardedForEnricher.enrich(spec, context);

        try {
            var jsonResponse = spec.retrieve().body(String.class);

            if (scopeDiscoveryServiceOptional.isEmpty()) {
                return ResponseEntity.ok().contentType(responseContentType).body(jsonResponse);
            }

            var discoveredScopes = scopeDiscoveryServiceOptional.get().getDiscoveredScopes();

            var json = (ObjectNode) objectMapper.readTree(jsonResponse);
            json.putPOJO("scopes_supported", discoveredScopes);
            return ResponseEntity.ok().contentType(responseContentType).body(json.toString());
        } catch (JsonProcessingException | RestClientException e) {
            LOGGER.error("Error proxying well-known endpoint: {}", path, e);
            var errorResponse = errorModelWriter.writeErrorModelAsJson(HttpStatus.INTERNAL_SERVER_ERROR);
            return ResponseEntity.internalServerError()
                    .contentType(responseContentType)
                    .body(errorResponse);
        }
    }

    private URI getWellKnownUri(String path) {
        return UriComponentsBuilder.fromUri(oAuthProperties.url())
                .path(path)
                .build()
                .toUri();
    }
}
