package com.infobip.openapi.mcp.auth.scope;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.ScopeProperties;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-auth-disabled")
public class ScopeDiscoveryDisabledTest extends OpenApiTestBase {

    @Test
    void shouldNotRegisterScopeServiceWhenAuthDisabled() {
        // Given
        var givenAuthEnabled = givenPropertyEnabled(AuthProperties.PREFIX);
        var givenOAuthEnabled = givenPropertyEnabled(OAuthProperties.PREFIX);
        var givenScopeDiscoveryEnabled = givenPropertyEnabled(ScopeProperties.PREFIX);

        // When
        var givenScopeServices = applicationContext.getBeansOfType(ScopeDiscoveryService.class);
        var givenMinimalSetCalculators = applicationContext.getBeansOfType(MinimalSetCalculator.class);

        // Then
        then(givenAuthEnabled).isFalse();
        then(givenOAuthEnabled).isTrue();
        then(givenScopeDiscoveryEnabled).isTrue();

        then(givenScopeServices).isEmpty();
        then(givenMinimalSetCalculators).isEmpty();
    }

    @Nested
    @ActiveProfiles("test-stdio")
    class ScopeDiscoveryStdioDisabledTest extends OpenApiTestBase {

        @Test
        void shouldNotHaveScopeConfigurationForStdio() {
            // then - scope configuration should not be active for STDIO
            then(applicationContext.getBeansOfType(ScopeProperties.class)).isEmpty();
        }

        @Test
        void shouldNotRegisterScopeDiscoveryServiceForStdio() {
            // then - scope discovery service should not be registered for STDIO transport
            then(applicationContext.getBeansOfType(ScopeDiscoveryService.class)).isEmpty();
        }

        @Test
        void shouldNotRegisterScopeCalculator() {
            // then - scope discovery calculator should not be registered for STDIO transport
            then(applicationContext.getBeansOfType(MinimalSetCalculator.class)).isEmpty();
        }
    }
}
