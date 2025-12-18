package com.infobip.openapi.mcp.auth.scope;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties.WwwAuthenticateProperties;
import com.infobip.openapi.mcp.auth.OAuthProperties.WwwAuthenticateProperties.UrlSource;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class WwwAuthenticateProviderTest {

    @Mock
    private OAuthProperties oAuthProperties;

    @Mock
    private OpenApiMcpProperties openApiMcpProperties;

    @Mock
    private XForwardedHostCalculator xForwardedHostCalculator;

    @Mock
    private ScopeDiscoveryService scopeDiscoveryService;

    private final MockHttpServletRequest givenRequest = new MockHttpServletRequest();
    private WwwAuthenticateProvider wwwAuthenticateProviderWithScopeService;
    private WwwAuthenticateProvider wwwAuthenticateProviderWithoutScopeService;

    @BeforeEach
    void setUp() {
        wwwAuthenticateProviderWithScopeService = new WwwAuthenticateProvider(
                oAuthProperties, Optional.of(scopeDiscoveryService), openApiMcpProperties, xForwardedHostCalculator);

        wwwAuthenticateProviderWithoutScopeService = new WwwAuthenticateProvider(
                oAuthProperties, Optional.empty(), openApiMcpProperties, xForwardedHostCalculator);
    }

    @Test
    void shouldBuildHeaderWithGivenApiBaseUrlWithPortAndPath() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com:1234/v1");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);

        // When
        var actualHeader = wwwAuthenticateProviderWithoutScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).startsWith("Bearer ");
        then(actualHeader).contains("resource_metadata=");
        then(actualHeader).contains("https://api.example.com:1234/v1/.well-known/oauth-protected-resource");
    }

    @Test
    void shouldBuildHeaderWithScopesWhenServicePresent() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com");
        var scopes = Set.of("read:users", "write:users", "admin:config");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);
        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(scopes);

        // When
        var actualHeader = wwwAuthenticateProviderWithScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).contains("scope=");
        then(actualHeader).contains("read:users");
        then(actualHeader).contains("write:users");
        then(actualHeader).contains("admin:config");
    }

    @Test
    void shouldNotIncludeScopesWhenScopeServiceAbsent() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);

        // When
        var actualHeader = wwwAuthenticateProviderWithoutScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).doesNotContain("scope=");
    }

    @Test
    void shouldNotIncludeScopesWhenNoScopesDiscovered() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);
        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(Set.of());

        // When
        var actualHeader = wwwAuthenticateProviderWithScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).doesNotContain("scope=");
    }

    @Test
    void shouldIncludeInsufficientScopeError() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);

        // When
        var actualHeader =
                wwwAuthenticateProviderWithoutScopeService.buildWwwAuthenticateHeaderWithScopeError(givenRequest);

        // Then
        then(actualHeader).contains("error=");
        then(actualHeader).contains("insufficient_scope");
    }

    @Test
    void shouldNotIncludeErrorWhenNotScopeError() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);

        // When
        var actualHeader = wwwAuthenticateProviderWithoutScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).doesNotContain("error=");
    }

    @Test
    void shouldBuildHeaderWithXForwardedHost() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.X_FORWARDED_HOST, false);
        var givenHostBuilder = UriComponentsBuilder.fromUriString("https://forwarded.example.com");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(xForwardedHostCalculator.hostBuilder(givenRequest)).willReturn(givenHostBuilder);

        // When
        var actualHeader = wwwAuthenticateProviderWithoutScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).contains("https://forwarded.example.com/.well-known");
        then(actualHeader).doesNotContain("mcp-root");
        BDDMockito.then(xForwardedHostCalculator).should().hostBuilder(givenRequest);
    }

    @Test
    void shouldIncludeMcpEndpointWhenConfiguredForXForwardedHost() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.X_FORWARDED_HOST, true);
        var givenHostBuilder = UriComponentsBuilder.fromUriString("https://forwarded.example.com/mcp-root");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(xForwardedHostCalculator.hostWithRootPathBuilder(givenRequest)).willReturn(givenHostBuilder);

        // When
        var actualHeader = wwwAuthenticateProviderWithoutScopeService.buildWwwAuthenticateHeader(givenRequest);

        // Then
        then(actualHeader).contains("https://forwarded.example.com/mcp-root");
        BDDMockito.then(xForwardedHostCalculator).should().hostWithRootPathBuilder(givenRequest);
    }

    @Test
    void shouldComplyWithRfc6750Format() {
        // Given
        var givenWwwAuthenticateProperties = new WwwAuthenticateProperties(UrlSource.API_BASE_URL, false);
        var givenApiBaseUrl = URI.create("https://api.example.com");
        var givenScopes = Set.of("read:users", "write:users");

        given(oAuthProperties.wwwAuthenticate()).willReturn(givenWwwAuthenticateProperties);
        given(openApiMcpProperties.apiBaseUrl()).willReturn(givenApiBaseUrl);
        given(scopeDiscoveryService.getDiscoveredScopes()).willReturn(givenScopes);

        // When
        var actualHeader =
                wwwAuthenticateProviderWithScopeService.buildWwwAuthenticateHeaderWithScopeError(givenRequest);

        // Then
        then(actualHeader)
                .matches("Bearer resource_metadata=\"[^\"]+\", scope=\"[^\"]+\", error=\"insufficient_scope\"");
    }
}
