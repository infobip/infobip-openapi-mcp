package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import com.infobip.openapi.mcp.auth.web.AuthorizationServerProxy;
import com.infobip.openapi.mcp.auth.web.CallbackPolicy;
import com.infobip.openapi.mcp.auth.web.HopByHopHeaders;
import com.infobip.openapi.mcp.auth.web.RejectedCallbackException;
import com.infobip.openapi.mcp.auth.web.Spec;
import com.infobip.openapi.mcp.auth.web.UpstreamEndpoints;
import com.infobip.openapi.mcp.auth.web.UpstreamEndpointsLoader;
import com.infobip.openapi.mcp.auth.web.WellKnownEndpoints;
import com.infobip.openapi.mcp.config.ApiBaseUrlProvider;
import com.infobip.openapi.mcp.config.HttpRequestFactories;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableConfigurationProperties(OAuthProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional({OAuthEnabledCondition.class, McpServerStdioDisabledCondition.class})
class OAuthConfiguration {

    @Bean
    @Qualifier(OAUTH_REST_CLIENT_QUALIFIER)
    @ConditionalOnMissingBean(name = OAUTH_REST_CLIENT_QUALIFIER)
    public RestClient oauthRestClient(OAuthProperties oAuthProperties) {
        var factory = HttpRequestFactories.create(oAuthProperties.connectTimeout(), oAuthProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultStatusHandler(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), (request, response) -> {
                    // Do not throw an exception for non-2xx responses, callers inspect the status themselves
                })
                .build();
    }

    @Bean
    public WwwAuthenticateProvider wwwAuthenticateProvider(
            OAuthProperties oAuthProperties,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            XForwardedHostCalculator xForwardedHostCalculator,
            ApiBaseUrlProvider apiBaseUrlProvider) {
        return new WwwAuthenticateProvider(
                oAuthProperties, scopeDiscoveryService, xForwardedHostCalculator, apiBaseUrlProvider);
    }

    @Bean
    public WellKnownEndpoints wellKnownEndpoints(
            @Qualifier(OAUTH_REST_CLIENT_QUALIFIER) RestClient oauthRestClient,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            XForwardedHostCalculator xForwardedHostCalculator,
            McpServerProperties mcpServerProperties) {
        return new WellKnownEndpoints(
                oauthRestClient,
                oAuthProperties,
                objectMapper,
                errorModelWriter,
                scopeDiscoveryService,
                enricherChain,
                contextFactory,
                xForwardedHostCalculator,
                mcpServerProperties);
    }

    /**
     * The authorization server metadata is also mapped with a path suffix, so that it can be discovered with path
     * insertion (RFC 8414 section 3.1) when the MCP server itself is advertised as the authorization server.
     * <p>
     * Every path answers {@code HEAD} and {@code OPTIONS} requests and rejects other methods with
     * {@code 405 Method Not Allowed}, the same as an annotated {@code GET} handler would.
     * </p>
     */
    @Bean
    public RouterFunction<ServerResponse> wellKnownEndpointsRouterFunction(WellKnownEndpoints wellKnownEndpoints) {
        var authorizationServer =
                (HandlerFunction<ServerResponse>) request -> toServerResponse(wellKnownEndpoints.proxyMetadata(
                        Spec.WellKnownPaths.OAUTH_AUTHORIZATION_SERVER, request.servletRequest()));
        var openidConfiguration = (HandlerFunction<ServerResponse>) request -> toServerResponse(
                wellKnownEndpoints.proxyMetadata(Spec.WellKnownPaths.OPENID_CONFIGURATION, request.servletRequest()));
        var protectedResource = (HandlerFunction<ServerResponse>) request -> ServerResponse.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, UTF_8))
                .body(wellKnownEndpoints.protectedResource(request.servletRequest()));

        var builder = RouterFunctions.route();
        getEndpoint(builder, Spec.WellKnownPaths.OAUTH_AUTHORIZATION_SERVER, authorizationServer);
        getEndpoint(builder, Spec.WellKnownPaths.OAUTH_AUTHORIZATION_SERVER + "/**", authorizationServer);
        getEndpoint(builder, Spec.WellKnownPaths.OPENID_CONFIGURATION, openidConfiguration);
        getEndpoint(builder, Spec.WellKnownPaths.OPENID_CONFIGURATION + "/**", openidConfiguration);
        getEndpoint(builder, Spec.WellKnownPaths.OAUTH_PROTECTED_RESOURCE, protectedResource);
        return builder.build();
    }

    private static void getEndpoint(
            RouterFunctions.Builder builder, String path, HandlerFunction<ServerResponse> handler) {
        builder.GET(path, handler)
                .HEAD(path, handler)
                .OPTIONS(path, request -> ServerResponse.ok()
                        .allow(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS)
                        .build())
                .route(RequestPredicates.path(path), request -> {
                    throw new HttpRequestMethodNotSupportedException(
                            request.method().name(), List.of(HttpMethod.GET.name()));
                });
    }

    /**
     * Reads the authorization server metadata while the context is being created, so the application fails to start
     * if it is unreachable or incomplete, and request threads only ever read the resolved endpoints.
     */
    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.PREFIX, name = OAuthProperties.PROXY_ENABLED)
    public UpstreamEndpoints upstreamEndpoints(
            @Qualifier(OAUTH_REST_CLIENT_QUALIFIER) RestClient oauthRestClient,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper) {
        return UpstreamEndpointsLoader.load(oauthRestClient, oAuthProperties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.PREFIX, name = OAuthProperties.PROXY_ENABLED)
    public AuthorizationServerProxy authorizationServerProxy(
            @Qualifier(OAUTH_REST_CLIENT_QUALIFIER) RestClient oauthRestClient,
            UpstreamEndpoints upstreamEndpoints,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            XForwardedHostCalculator xForwardedHostCalculator,
            CallbackPolicy callbackPolicy) {
        return new AuthorizationServerProxy(
                oauthRestClient,
                upstreamEndpoints,
                objectMapper,
                errorModelWriter,
                scopeDiscoveryService,
                enricherChain,
                contextFactory,
                xForwardedHostCalculator,
                callbackPolicy);
    }

    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.PREFIX, name = OAuthProperties.PROXY_ENABLED)
    public RouterFunction<ServerResponse> authorizationServerProxyRouterFunction(
            AuthorizationServerProxy authorizationServerProxy, XForwardedHostCalculator xForwardedHostCalculator) {
        var rootPath = xForwardedHostCalculator.rootPath();
        return RouterFunctions.route()
                .POST(
                        rootPath + Spec.Paths.REGISTER,
                        request -> toServerResponse(authorizationServerProxy.register(
                                request.servletRequest(), request.body(byte[].class))))
                .GET(
                        rootPath + Spec.Paths.AUTHORIZE,
                        request -> toServerResponse(authorizationServerProxy.authorize(request.servletRequest())))
                .GET(
                        rootPath + Spec.Paths.CALLBACK + "/{encodedRedirectUri}",
                        request -> toServerResponse(authorizationServerProxy.callback(
                                request.servletRequest(), request.pathVariable("encodedRedirectUri"))))
                .POST(
                        rootPath + Spec.Paths.TOKEN,
                        request -> toServerResponse(authorizationServerProxy.token(request.servletRequest())))
                .onError(
                        RejectedCallbackException.class,
                        (e, request) -> toServerResponse(authorizationServerProxy.rejectedCallback(
                                request.servletRequest(), (RejectedCallbackException) e)))
                .build();
    }

    private static <T> ServerResponse toServerResponse(ResponseEntity<T> responseEntity) {
        var response = ServerResponse.status(responseEntity.getStatusCode())
                .headers(headers -> responseEntity.getHeaders().forEach((name, values) -> {
                    if (!HopByHopHeaders.isHopByHop(name)) {
                        headers.addAll(name, values);
                    }
                }));
        var body = responseEntity.getBody();
        return body == null ? response.build() : response.body(body);
    }
}
