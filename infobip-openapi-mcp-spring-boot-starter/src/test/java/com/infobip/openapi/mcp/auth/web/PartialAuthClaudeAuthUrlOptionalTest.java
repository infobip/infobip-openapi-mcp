package com.infobip.openapi.mcp.auth.web;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves that {@code auth-url} is truly optional in {@code PARTIAL_AUTH_CLAUDE_COMPATIBLE} mode: this context starts with
 * authentication enabled, no {@code auth-url} configured at all, and the filter is registered as usual.
 * With the previous mandatory {@code auth-url} validation the context would fail to start.
 */
@ActiveProfiles("test-partial-auth-claude")
class PartialAuthClaudeAuthUrlOptionalTest extends OpenApiTestBase {

    @Test
    void shouldStartWithoutAuthUrlAndRegisterFilter() {
        // Then
        var authProperties = applicationContext.getBean(AuthProperties.class);
        then(authProperties.mode()).isEqualTo(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
        then(authProperties.authUrl()).isNull();
        then(applicationContext.getBeansOfType(FilterRegistrationBean.class).values().stream()
                        .map(FilterRegistrationBean::getFilter)
                        .map(bean -> (Class) bean.getClass())
                        .toList())
                .contains(InitialAuthenticationFilter.class);
    }
}
