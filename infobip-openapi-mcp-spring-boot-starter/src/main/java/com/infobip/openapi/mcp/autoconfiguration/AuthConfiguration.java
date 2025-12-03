package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.AUTHORIZATION_REST_CLIENT_QUALIFIER;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AuthProperties.class)
@Conditional({AuthEnabledCondition.class, McpServerStdioDisabledCondition.class})
class AuthConfiguration {

    @Bean
    @Qualifier(AUTHORIZATION_REST_CLIENT_QUALIFIER)
    public RestClient openapiMcpAuthorizationRestClientBuilder(AuthProperties authProperties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(authProperties.connectTimeout());
        factory.setReadTimeout(authProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultStatusHandler(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), (request, response) -> {
                    // Do not throw an exception for non-2xx responses
                })
                .build();
    }

    @Bean
    public FilterRegistrationBean<InitialAuthenticationFilter> initialAuthenticationFilterRegistration(
            @Qualifier(AUTHORIZATION_REST_CLIENT_QUALIFIER) RestClient restClient,
            AuthProperties authProperties,
            Optional<OAuthProperties> oAuthProperties,
            ErrorModelWriter errorModelWriter,
            McpServerProperties mcpServerProperties,
            OpenApiMcpProperties openApiMcpProperties,
            Optional<McpServerSseProperties> mcpServerSseProperties,
            Optional<McpServerStreamableHttpProperties> mcpServerStreamableHttpProperties,
            ApiRequestEnricherChain enricherChain,
            McpRequestContextFactory contextFactory,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            XForwardedHostCalculator xForwardedHostCalculator) {
        var filter = new InitialAuthenticationFilter(
                restClient,
                authProperties,
                oAuthProperties,
                openApiMcpProperties,
                errorModelWriter,
                enricherChain,
                contextFactory,
                scopeDiscoveryService,
                xForwardedHostCalculator);

        var registration = new FilterRegistrationBean<>(filter);
        registration.setUrlPatterns(
                provideUrlPatterns(mcpServerProperties, mcpServerSseProperties, mcpServerStreamableHttpProperties));
        return registration;
    }

    private Collection<String> provideUrlPatterns(
            McpServerProperties mcpServerProperties,
            Optional<McpServerSseProperties> mcpServerSseProperties,
            Optional<McpServerStreamableHttpProperties> mcpServerStreamableHttpProperties) {
        if (!mcpServerProperties.isEnabled() || mcpServerProperties.isStdio()) {
            return List.of();
        }

        switch (mcpServerProperties.getProtocol()) {
            case STATELESS, STREAMABLE -> {
                return mcpServerStreamableHttpProperties
                        .map(McpServerStreamableHttpProperties::getMcpEndpoint)
                        .map(List::of)
                        .orElse(List.of());
            }
            case SSE -> {
                // todo: Process SSE base path properly
                return mcpServerSseProperties
                        .map(properties -> List.of(properties.getSseEndpoint(), properties.getSseMessageEndpoint()))
                        .orElse(List.of());
            }
            default -> {
                return List.of();
            }
        }
    }
}
