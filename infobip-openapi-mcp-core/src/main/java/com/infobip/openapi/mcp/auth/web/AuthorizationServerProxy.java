package com.infobip.openapi.mcp.auth.web;

import static com.infobip.openapi.mcp.auth.web.Spec.ClientMetadata.REDIRECT_URIS;
import static com.infobip.openapi.mcp.auth.web.Spec.ClientMetadata.SCOPE;
import static com.infobip.openapi.mcp.auth.web.Spec.GrantTypes.AUTHORIZATION_CODE;
import static com.infobip.openapi.mcp.auth.web.Spec.Parameters.GRANT_TYPE;
import static com.infobip.openapi.mcp.auth.web.Spec.Parameters.ISS;
import static com.infobip.openapi.mcp.auth.web.Spec.Parameters.REDIRECT_URI;
import static com.infobip.openapi.mcp.auth.web.Spec.TokenResponse.ID_TOKEN;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stateless proxy of the OAuth endpoints that MCP clients use to obtain tokens: dynamic client registration
 * (RFC 7591), authorization, authorization callback and token endpoints.
 * <p>
 * The MCP server presents itself as the authorization server, so that:
 * </p>
 * <ul>
 *     <li>client registrations are forwarded with the {@code scope} replaced by the scopes required by the MCP server,
 *     because some MCP clients register without (or with the wrong) scopes,</li>
 *     <li>authorization responses carry the MCP server as the {@code iss} parameter (RFC 9207), matching the issuer in
 *     the proxied authorization server metadata.</li>
 * </ul>
 * <p>
 * To intercept authorization responses without keeping any state, the redirect URIs of clients are replaced with
 * callback URIs of the MCP server that embed the original redirect URI ({@code <issuer>/oauth/callback/<base64url>}).
 * The authorization server only ever sees the callback URIs, both at registration, authorization and token requests,
 * while clients only ever see their own redirect URIs.
 * </p>
 * <p>
 * The upstream endpoints are resolved by the caller (see {@link UpstreamEndpointsLoader}) and never change afterwards.
 * </p>
 */
public class AuthorizationServerProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerProxy.class);

    private static final List<String> FORWARDED_REQUEST_HEADERS =
            List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT);
    private static final List<String> FORWARDED_RESPONSE_HEADERS =
            List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL, HttpHeaders.PRAGMA);
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final RestClient upstreamRestClient;
    private final ObjectMapper objectMapper;
    private final ErrorModelWriter errorModelWriter;
    private final Optional<ScopeDiscoveryService> scopeDiscoveryService;
    private final ApiRequestEnricherChain enricherChain;
    private final McpRequestContextFactory contextFactory;
    private final XForwardedHostCalculator xForwardedHostCalculator;
    private final CallbackPolicy callbackPolicy;

    private final UpstreamEndpoints upstreamEndpoints;

    /**
     * @param upstreamRestClient client used to forward registration and token requests; must not throw on non-2xx
     *                           responses
     * @param upstreamEndpoints  endpoints of the authorization server that requests are forwarded to
     */
    public AuthorizationServerProxy(
            RestClient upstreamRestClient,
            UpstreamEndpoints upstreamEndpoints,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            XForwardedHostCalculator xForwardedHostCalculator,
            CallbackPolicy callbackPolicy) {
        this.upstreamRestClient = upstreamRestClient;
        this.upstreamEndpoints = upstreamEndpoints;
        this.objectMapper = objectMapper;
        this.errorModelWriter = errorModelWriter;
        this.scopeDiscoveryService = scopeDiscoveryService;
        this.enricherChain = enricherChain;
        this.contextFactory = contextFactory;
        this.xForwardedHostCalculator = xForwardedHostCalculator;
        this.callbackPolicy = callbackPolicy;
        if (scopeDiscoveryService.isEmpty()) {
            LOGGER.warn("OAuth scope discovery is disabled, client registration requests will be proxied without "
                    + "changing the requested scopes.");
        }
    }

    /**
     * Returns the issuer the MCP server presents to clients: the URL of the MCP server, which is also advertised as the
     * authorization server in the protected resource metadata.
     */
    public String issuer(HttpServletRequest request) {
        return xForwardedHostCalculator.hostWithRootPathBuilder(request).toUriString();
    }

    /**
     * Forwards the client registration request to the authorization server, with the scopes required by the MCP server
     * and the client's redirect URIs replaced by callback URIs of the MCP server.
     *
     * @param request the incoming client registration request
     * @param body    the raw request body
     * @return the authorization server response with the original redirect URIs, or an error response if the request
     * could not be forwarded
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
        var issuer = issuer(request);
        mapRedirectUris(clientMetadata, redirectUri -> validateAndEncodeRedirectUri(issuer, redirectUri));
        LOGGER.info(
                "Proxying OAuth client registration for client '{}', requested scope '{}', forwarded scope '{}'.",
                clientMetadata.path("client_name").asString(null),
                requestedScope,
                clientMetadata.path(SCOPE).asString(null));

        try {
            var spec = upstreamRestClient
                    .post()
                    .uri(upstreamEndpoints.registration())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsBytes(clientMetadata));
            var upstreamResponse = exchange(spec, request);
            return withResponseJson(upstreamResponse, json -> {
                mapRedirectUris(json, redirectUri -> decodeRedirectUri(issuer, redirectUri));
                return json;
            });
        } catch (RestClientException | JacksonException e) {
            return badGateway("client registration", request, e);
        }
    }

    /**
     * Redirects the authorization request to the authorization server, with the redirect URI replaced by the callback
     * URI of the MCP server.
     *
     * @param request the incoming authorization request
     * @return a redirect to the authorization server
     */
    public ResponseEntity<byte[]> authorize(HttpServletRequest request) {
        var issuer = issuer(request);
        var query = encodeParameters(
                request.getParameterMap(),
                (name, value) -> REDIRECT_URI.equals(name) ? validateAndEncodeRedirectUri(issuer, value) : value);
        return redirect(appendQuery(upstreamEndpoints.authorization().toString(), query));
    }

    /**
     * Redirects the authorization response from the authorization server back to the client's original redirect URI,
     * with the {@code iss} parameter set to the issuer of the MCP server.
     *
     * @param request           the incoming authorization response
     * @param encodedRedirectUri the last path segment of the callback URI, i.e. the encoded original redirect URI
     * @return a redirect to the client, or a bad request response if the redirect URI cannot be decoded
     */
    public ResponseEntity<byte[]> callback(HttpServletRequest request, String encodedRedirectUri) {
        String redirectUri;
        try {
            redirectUri = new String(BASE64_DECODER.decode(encodedRedirectUri), StandardCharsets.UTF_8);
            var uri = URI.create(redirectUri);
            if (!callbackPolicy.test(uri)) {
                throw RejectedCallbackException.becausePolicyFailed(uri);
            }
        } catch (IllegalArgumentException e) {
            throw RejectedCallbackException.becauseUriIsInvalid(encodedRedirectUri, e);
        }

        var issuer = issuer(request);
        var query = encodeParameters(request.getParameterMap(), (name, value) -> ISS.equals(name) ? issuer : value);
        return redirect(appendQuery(redirectUri, query));
    }

    /**
     * Forwards the token request to the authorization server. For the authorization code grant, the redirect URI is
     * replaced by the callback URI of the MCP server, as used in the authorization request. The {@code id_token} is
     * removed from the response, since it is issued by the authorization server and its {@code iss} claim doesn't
     * match the issuer of the MCP server.
     *
     * @param request the incoming token request
     * @return the authorization server response, or an error response if the request could not be forwarded
     */
    public ResponseEntity<byte[]> token(HttpServletRequest request) {
        try {
            var issuer = issuer(request);
            var authorizationCodeGrant = AUTHORIZATION_CODE.equals(request.getParameter(GRANT_TYPE));
            var form = encodeParameters(
                    request.getParameterMap(),
                    (name, value) -> authorizationCodeGrant && REDIRECT_URI.equals(name)
                            ? validateAndEncodeRedirectUri(issuer, value)
                            : value);
            var spec = upstreamRestClient
                    .post()
                    .uri(upstreamEndpoints.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form.getBytes(StandardCharsets.UTF_8));
            var upstreamResponse = exchange(spec, request);
            return withResponseJson(upstreamResponse, json -> {
                json.remove(ID_TOKEN);
                return json;
            });
        } catch (RestClientException e) {
            return badGateway("token request", request, e);
        }
    }

    /**
     * Builds the response for a request whose redirect URI was rejected by the {@link CallbackPolicy} or is not a valid
     * URI. It is an error response and never a redirect, since the redirect URI can't be trusted.
     * <p>
     * Registration requests are answered with {@code invalid_redirect_uri} (RFC 7591), all other requests with
     * {@code invalid_request} (RFC 6749).
     * </p>
     *
     * @param request the request that was rejected
     * @param e       the rejection raised while handling the request
     * @return a bad request response with an OAuth error
     */
    public ResponseEntity<byte[]> rejectedCallback(HttpServletRequest request, RejectedCallbackException e) {
        LOGGER.warn("Rejected OAuth request to '{}': {}", request.getRequestURI(), e.getMessage());
        if (request.getRequestURI().endsWith(Spec.Paths.REGISTER)) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_redirect_uri", "Redirect URI is not allowed.");
        }
        return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "Redirect URI is not allowed.");
    }

    private ResponseEntity<byte[]> exchange(RestClient.RequestBodySpec spec, HttpServletRequest request) {
        for (var headerName : FORWARDED_REQUEST_HEADERS) {
            var headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                spec.header(headerName, headerValue);
            }
        }
        var enrichedSpec = enricherChain.enrich(spec, contextFactory.forServletFilter(request));

        var upstreamResponse = enrichedSpec.retrieve().toEntity(byte[].class);
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
    }

    /**
     * Applies the modification to successful JSON object responses; other responses are returned as is.
     */
    private ResponseEntity<byte[]> withResponseJson(
            ResponseEntity<byte[]> response, UnaryOperator<ObjectNode> modification) {
        var body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0) {
            return response;
        }
        try {
            if (!(objectMapper.readTree(body) instanceof ObjectNode json)) {
                return response;
            }
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(objectMapper.writeValueAsBytes(modification.apply(json)));
        } catch (JacksonException e) {
            return response;
        }
    }

    private void mapRedirectUris(ObjectNode json, UnaryOperator<String> mapping) {
        if (json.get(REDIRECT_URIS) instanceof ArrayNode redirectUris) {
            for (var i = 0; i < redirectUris.size(); i++) {
                if (redirectUris.get(i).isString()) {
                    redirectUris.set(i, mapping.apply(redirectUris.get(i).asString()));
                }
            }
        }
    }

    private String validateAndEncodeRedirectUri(String issuer, String redirectUri) {
        try {
            var uri = URI.create(redirectUri);
            if (!callbackPolicy.test(uri)) {
                throw RejectedCallbackException.becausePolicyFailed(uri);
            }
            return issuer + Spec.Paths.CALLBACK + "/"
                    + BASE64_ENCODER.encodeToString(redirectUri.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw RejectedCallbackException.becauseUriIsInvalid(redirectUri, e);
        }
    }

    /**
     * Decodes callback URIs of this MCP server back to the original redirect URIs; other URIs are returned as is.
     */
    private String decodeRedirectUri(String issuer, String redirectUri) {
        var prefix = issuer + Spec.Paths.CALLBACK + "/";
        if (!redirectUri.startsWith(prefix)) {
            return redirectUri;
        }
        try {
            return new String(BASE64_DECODER.decode(redirectUri.substring(prefix.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return redirectUri;
        }
    }

    private interface ParameterMapping {
        String map(String name, String value);
    }

    private static String encodeParameters(Map<String, String[]> parameters, ParameterMapping mapping) {
        var joiner = new StringJoiner("&");
        parameters.forEach((name, values) -> {
            for (var value : values) {
                joiner.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(mapping.map(name, value), StandardCharsets.UTF_8));
            }
        });
        return joiner.toString();
    }

    private static String appendQuery(String uri, String query) {
        if (query.isEmpty()) {
            return uri;
        }
        return uri + (uri.contains("?") ? "&" : "?") + query;
    }

    private static ResponseEntity<byte[]> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private ResponseEntity<byte[]> badGateway(String operation, HttpServletRequest request, Exception e) {
        LOGGER.error("Error proxying OAuth {}.", operation, e);
        var errorResponse = errorModelWriter.writeErrorModelAsJson(HttpStatus.BAD_GATEWAY, request, e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> invalidClientMetadata(String description) {
        return oauthError(HttpStatus.BAD_REQUEST, "invalid_client_metadata", description);
    }

    private ResponseEntity<byte[]> oauthError(HttpStatus status, String error, String description) {
        var json = objectMapper.createObjectNode().put("error", error).put("error_description", description);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsBytes(json));
    }
}
