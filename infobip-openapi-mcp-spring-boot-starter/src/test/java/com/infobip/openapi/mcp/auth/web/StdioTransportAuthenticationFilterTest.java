package com.infobip.openapi.mcp.auth.web;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.AUTHORIZATION_REST_CLIENT_QUALIFIER;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-stdio")
class StdioTransportAuthenticationFilterTest extends OpenApiTestBase {

    @Test
    void shouldNotRegisterAuthenticationFilterForStdio() {
        // then - filter should not be registered for STDIO transport
        then(applicationContext.getBeansOfType(FilterRegistrationBean.class).values().stream()
                        .map(FilterRegistrationBean::getFilter)
                        .map(bean -> (Class) bean.getClass())
                        .toList())
                .doesNotContain(InitialAuthenticationFilter.class);
    }

    @Test
    void shouldNotRegisterAuthRestClientForStdio() {
        // then - auth REST client should not be registered for STDIO transport
        then(applicationContext.containsBean(AUTHORIZATION_REST_CLIENT_QUALIFIER))
                .isFalse();
    }
}
