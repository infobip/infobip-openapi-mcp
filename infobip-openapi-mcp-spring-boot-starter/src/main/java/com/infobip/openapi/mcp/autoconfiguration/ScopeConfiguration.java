package com.infobip.openapi.mcp.autoconfiguration;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import com.infobip.openapi.mcp.auth.scope.MinimalSetCalculator;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@EnableConfigurationProperties(ScopeProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional({ScopeEnabledCondition.class, McpServerStdioDisabledCondition.class})
class ScopeConfiguration {

    @Bean
    public MinimalSetCalculator minimalSetCalculator() {
        return new MinimalSetCalculator();
    }

    @Bean
    public ScopeDiscoveryService scopeDiscoveryService(
            ScopeProperties scopeProperties,
            OpenApiRegistry openApiRegistry,
            MinimalSetCalculator minimalSetCalculator) {
        return new ScopeDiscoveryService(scopeProperties, openApiRegistry, minimalSetCalculator);
    }
}
