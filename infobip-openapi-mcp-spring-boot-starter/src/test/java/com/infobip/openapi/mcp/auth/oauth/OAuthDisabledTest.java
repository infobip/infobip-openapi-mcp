package com.infobip.openapi.mcp.auth.oauth;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import com.infobip.openapi.mcp.auth.web.WellKnownEndpoints;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-auth-disabled")
class OAuthDisabledTest extends OpenApiTestBase {

    private static final String WELL_KNOWN_ROUTER_FUNCTION_BEAN = "wellKnownEndpointsRouterFunction";

    @Test
    void shouldNotRegisterWellKnownEndpointsAndRestClientAndWwwAuthenticateProvider() {
        // Given
        var givenAuthEnabled = givenPropertyEnabled(AuthProperties.PREFIX);
        var givenOAuthEnabled = givenPropertyEnabled(OAuthProperties.PREFIX);

        // When
        var wellKnownEndpoints = applicationContext.getBeansOfType(WellKnownEndpoints.class);
        var wellKnownRouter = applicationContext.containsBean(WELL_KNOWN_ROUTER_FUNCTION_BEAN);
        var routerExits = applicationContext.containsBean(OAUTH_REST_CLIENT_QUALIFIER);
        var wwwAuthenticateProvider = applicationContext.getBeansOfType(WwwAuthenticateProvider.class);

        // Then
        then(givenAuthEnabled).isFalse();
        then(givenOAuthEnabled).isTrue();

        then(wellKnownEndpoints).isEmpty();
        then(wellKnownRouter).isFalse();
        then(routerExits).isFalse();
        then(wwwAuthenticateProvider).isEmpty();
    }

    @Nested
    @ActiveProfiles("test-stdio")
    class OAuthStdioDisabledTest extends OpenApiTestBase {

        @Test
        void shouldNotRegisterOAuthRestClientForStdio() {
            // then - Oauth REST client should not be registered for STDIO transport
            then(applicationContext.containsBean(OAUTH_REST_CLIENT_QUALIFIER)).isFalse();
        }

        @Test
        void shouldNotHaveOAuthConfigurationForStdio() {
            // then - auth configuration should not be active for STDIO
            then(applicationContext.getBeansOfType(OAuthProperties.class)).isEmpty();
        }

        @Test
        void shouldNotRegisterOAuthRouterForStdio() {
            // then - Oauth router should not be registered for STDIO transport
            then(applicationContext.getBeansOfType(WellKnownEndpoints.class)).isEmpty();
            then(applicationContext.containsBean(WELL_KNOWN_ROUTER_FUNCTION_BEAN))
                    .isFalse();
        }
    }
}
