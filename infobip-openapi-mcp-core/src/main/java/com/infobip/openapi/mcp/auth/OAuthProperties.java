package com.infobip.openapi.mcp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * OAuth configuration properties for OpenAPI MCP Server.
 *
 * @param enabled        Enable OAuth authentication. Default is false.
 * @param url            OAuth server URL to check for well-known configuration.
 * @param connectTimeout Connection timeout for the validation API call. Default is 5 seconds.
 * @param readTimeout    Read timeout for the validation API call. Default is 5 seconds.
 * @param scopeDiscovery Configuration properties for OAuth scopes.
 * @param registrationProxy Configuration properties for proxying OAuth dynamic client registration.
 */
@Validated
@ConfigurationProperties(prefix = OAuthProperties.PREFIX)
public record OAuthProperties(
        boolean enabled,
        @NotNull URI url,
        Duration connectTimeout,
        Duration readTimeout,
        @NestedConfigurationProperty @Valid WwwAuthenticateProperties wwwAuthenticate,
        @NestedConfigurationProperty @Valid ScopeProperties scopeDiscovery,
        @NestedConfigurationProperty @Valid RegistrationProxyProperties registrationProxy) {

    public static final String PREFIX = AuthProperties.PREFIX + ".oauth";
    public static final String WELL_KNOWN_PATH = "/.well-known/oauth-protected-resource";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Constructor with defaults for optional properties.
     */
    public OAuthProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_TIMEOUT;
        }
        if (registrationProxy == null) {
            registrationProxy = new RegistrationProxyProperties(false, null, null);
        }
    }

    /**
     * OAuth dynamic client registration (RFC 7591) proxy properties.
     * <p>
     * When enabled, the MCP server advertises itself as the authorization server in the protected resource metadata,
     * points {@code registration_endpoint} of the proxied authorization server metadata to itself, and forwards client
     * registration requests to the authorization server with the {@code scope} replaced by the scopes required by the
     * MCP server. All other OAuth endpoints (authorization, token, etc.) keep pointing to the authorization server.
     * </p>
     *
     * @param enabled     Enable the client registration proxy. Default is false.
     * @param path        Path of the client registration endpoint, relative to the MCP endpoint. Default is
     *                    {@code /oauth/register}.
     * @param upstreamUrl Client registration endpoint of the authorization server. When not set, it is read from the
     *                    {@code registration_endpoint} of the authorization server's OpenID configuration.
     */
    public record RegistrationProxyProperties(
            boolean enabled, String path, @Nullable URI upstreamUrl) {

        public static final String PREFIX = OAuthProperties.PREFIX + ".registration-proxy";
        public static final String DEFAULT_PATH = "/oauth/register";

        public RegistrationProxyProperties {
            if (path == null || path.isBlank()) {
                path = DEFAULT_PATH;
            }
        }
    }

    /**
     * WWW-Authenticate header URL properties.
     *
     * @param urlSource          Value for the WWW-Authenticate header to be used in responses. In case of
     *                           X_FORWARDED_HOST, the header will be constructed based on the incoming request's
     *                           X-Forwarded-Host header. If the header is missing, Host header will be used instead,
     *                           and if that is also missing, the API base URL from configuration will be used.
     *                           Default is API_BASE_URL.
     * @param includeMcpEndpoint Whether to include the MCP endpoint in the WWW-Authenticate header URL. Default is false.
     */
    public record WwwAuthenticateProperties(UrlSource urlSource, boolean includeMcpEndpoint) {

        public static final UrlSource DEFAULT_WWW_AUTHENTICATE_URL_SOURCE = UrlSource.API_BASE_URL;

        public WwwAuthenticateProperties {
            if (urlSource == null) {
                urlSource = DEFAULT_WWW_AUTHENTICATE_URL_SOURCE;
            }
        }

        public enum UrlSource {
            API_BASE_URL,
            X_FORWARDED_HOST
        }
    }
}
