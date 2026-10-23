package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REGISTRATION_REST_CLIENT_QUALIFIER;
import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import com.infobip.openapi.mcp.auth.web.AuthorizationServerProxy;
import com.infobip.openapi.mcp.auth.web.Spec;
import com.infobip.openapi.mcp.auth.web.UpstreamEndpoints;
import com.infobip.openapi.mcp.auth.web.UpstreamEndpointsLoader;
import com.infobip.openapi.mcp.config.ApiBaseUrlProvider;
import com.infobip.openapi.mcp.config.HttpRequestFactories;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.util.Optional;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
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

        return RestClient.builder().requestFactory(factory).build();
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
    @Qualifier(OAUTH_REGISTRATION_REST_CLIENT_QUALIFIER)
    @ConditionalOnMissingBean(name = OAUTH_REGISTRATION_REST_CLIENT_QUALIFIER)
    @ConditionalOnProperty(prefix = OAuthProperties.PREFIX, name = OAuthProperties.PROXY_ENABLED)
    public RestClient oauthRegistrationRestClient(OAuthProperties oAuthProperties) {
        var factory = HttpRequestFactories.create(oAuthProperties.connectTimeout(), oAuthProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultStatusHandler(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), (request, response) -> {
                    // Do not throw an exception for non-2xx responses, they are returned to the client as is
                })
                .build();
    }

    /**
     * Reads the authorization server metadata while the context is being created, so the application fails to start
     * if it is unreachable or incomplete, and request threads only ever read the resolved endpoints.
     */
    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.PREFIX, name = OAuthProperties.PROXY_ENABLED)
    public UpstreamEndpoints upstreamEndpoints(
            @Qualifier(OAUTH_REST_CLIENT_QUALIFIER) RestClient metadataRestClient,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper) {
        return UpstreamEndpointsLoader.load(metadataRestClient, oAuthProperties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.PREFIX, name = OAuthProperties.PROXY_ENABLED)
    public AuthorizationServerProxy authorizationServerProxy(
            @Qualifier(OAUTH_REGISTRATION_REST_CLIENT_QUALIFIER) RestClient upstreamRestClient,
            UpstreamEndpoints upstreamEndpoints,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            XForwardedHostCalculator xForwardedHostCalculator) {
        return new AuthorizationServerProxy(
                upstreamRestClient,
                upstreamEndpoints,
                objectMapper,
                errorModelWriter,
                scopeDiscoveryService,
                enricherChain,
                contextFactory,
                xForwardedHostCalculator);
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
                .build();
    }

    private static ServerResponse toServerResponse(ResponseEntity<byte[]> responseEntity) {
        var response = ServerResponse.status(responseEntity.getStatusCode())
                .headers(headers -> headers.addAll(responseEntity.getHeaders()));
        var body = responseEntity.getBody();
        return body == null ? response.build() : response.body(body);
    }
}
