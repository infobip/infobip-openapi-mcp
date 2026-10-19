package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REGISTRATION_REST_CLIENT_QUALIFIER;
import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import com.infobip.openapi.mcp.auth.web.ClientRegistrationProxy;
import com.infobip.openapi.mcp.config.ApiBaseUrlProvider;
import com.infobip.openapi.mcp.config.HttpRequestFactories;
import com.infobip.openapi.mcp.enricher.XForwardedForEnricher;
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
    @ConditionalOnProperty(prefix = OAuthProperties.RegistrationProxyProperties.PREFIX, name = "enabled")
    public RestClient oauthRegistrationRestClient(OAuthProperties oAuthProperties) {
        var factory = HttpRequestFactories.create(oAuthProperties.connectTimeout(), oAuthProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultStatusHandler(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), (request, response) -> {
                    // Do not throw an exception for non-2xx responses, they are returned to the client as is
                })
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.RegistrationProxyProperties.PREFIX, name = "enabled")
    public ClientRegistrationProxy clientRegistrationProxy(
            @Qualifier(OAUTH_REGISTRATION_REST_CLIENT_QUALIFIER) RestClient registrationRestClient,
            @Qualifier(OAUTH_REST_CLIENT_QUALIFIER) RestClient metadataRestClient,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper,
            ErrorModelWriter errorModelWriter,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            XForwardedForEnricher xForwardedForEnricher,
            McpRequestContextFactory contextFactory) {
        return new ClientRegistrationProxy(
                registrationRestClient,
                metadataRestClient,
                oAuthProperties,
                objectMapper,
                errorModelWriter,
                scopeDiscoveryService,
                xForwardedForEnricher,
                contextFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = OAuthProperties.RegistrationProxyProperties.PREFIX, name = "enabled")
    public RouterFunction<ServerResponse> clientRegistrationProxyRouterFunction(
            ClientRegistrationProxy clientRegistrationProxy,
            OAuthProperties oAuthProperties,
            XForwardedHostCalculator xForwardedHostCalculator) {
        var path = xForwardedHostCalculator.rootPath()
                + oAuthProperties.registrationProxy().path();
        return RouterFunctions.route()
                .POST(path, request -> {
                    var upstreamResponse =
                            clientRegistrationProxy.register(request.servletRequest(), request.body(byte[].class));
                    var response = ServerResponse.status(upstreamResponse.getStatusCode())
                            .headers(headers -> headers.addAll(upstreamResponse.getHeaders()));
                    var body = upstreamResponse.getBody();
                    return body == null ? response.build() : response.body(body);
                })
                .build();
    }
}
