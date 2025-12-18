package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.util.Optional;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@EnableConfigurationProperties(OAuthProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional({OAuthEnabledCondition.class, McpServerStdioDisabledCondition.class})
class OAuthConfiguration {

    @Bean
    @Qualifier(OAUTH_REST_CLIENT_QUALIFIER)
    public RestClient oauthRestClient(OAuthProperties oAuthProperties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(oAuthProperties.connectTimeout());
        factory.setReadTimeout(oAuthProperties.readTimeout());

        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public WwwAuthenticateProvider wwwAuthenticateProvider(
            OAuthProperties oAuthProperties,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            OpenApiMcpProperties openApiMcpProperties,
            XForwardedHostCalculator xForwardedHostCalculator) {
        return new WwwAuthenticateProvider(
                oAuthProperties, scopeDiscoveryService, openApiMcpProperties, xForwardedHostCalculator);
    }
}
