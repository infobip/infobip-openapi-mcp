package com.infobip.openapi.mcp.auth.oauth;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.OAUTH_REST_CLIENT_QUALIFIER;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.autoconfiguration.OAuthController;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-auth-disabled")
class OAuthDisabledTest extends OpenApiTestBase {

    @Test
    void shouldNotRegisterOAuthRouterAndRestClient() {
        // Given
        var givenAuthEnabled = givenPropertyEnabled(AuthProperties.PREFIX);
        var givenOAuthEnabled = givenPropertyEnabled(OAuthProperties.PREFIX);

        // When
        var controller = applicationContext.getBeansOfType(OAuthController.class);
        var routerExits = applicationContext.containsBean(OAUTH_REST_CLIENT_QUALIFIER);

        // Then
        then(givenAuthEnabled).isFalse();
        then(givenOAuthEnabled).isTrue();

        then(controller).isEmpty();
        then(routerExits).isFalse();
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
            then(applicationContext.getBeansOfType(OAuthController.class)).isEmpty();
        }
    }
}
