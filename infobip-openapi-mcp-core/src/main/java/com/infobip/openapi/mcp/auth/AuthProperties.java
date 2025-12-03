package com.infobip.openapi.mcp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Authentication configuration properties for OpenAPI MCP Server.
 *
 * @param enabled                  Enable API authentication. Default is true.
 * @param authUrl                  The API endpoint URL to validate credentials against.
 * @param connectTimeout           Connection timeout for the validation API call. Default is 5 seconds.
 * @param readTimeout              Read timeout for the validation API call. Default is 5 seconds.
 * @param overrideExternalResponse Whether to override the external response with the internal one.
 *                                 This is useful for cases where you want to control the response format
 *                                 or content returned to the client. If set to true, the response will
 *                                 be overridden by the model provided by {@link com.infobip.openapi.mcp.error.ErrorModelProvider}.
 *                                 Default is true.
 */
@Validated
@ConfigurationProperties(prefix = AuthProperties.PREFIX)
public record AuthProperties(
        boolean enabled,
        @NotNull URI authUrl,
        Duration connectTimeout,
        Duration readTimeout,
        boolean overrideExternalResponse,
        @NestedConfigurationProperty @Valid OAuthProperties oauth) {

    public static final String PREFIX = "infobip.openapi.mcp.security.auth";

    /**
     * Constructor with defaults for optional properties.
     */
    public AuthProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
