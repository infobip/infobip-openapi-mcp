package com.infobip.openapi.mcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Scope discovery properties for OpenAPI MCP Server.
 *
 * @param enabled         Enable OAuth scope discovery. Default is false.
 * @param scopeExtensions Scope extensions to read scopes from. Default is an empty string.
 * @param mandatoryScopes Mandatory scopes that must be present. Scopes should be comma-separated.
 *                        Default is an empty string.
 */
@Validated
@ConfigurationProperties(prefix = ScopeProperties.PREFIX)
public record ScopeProperties(
        boolean enabled, String scopeExtensions, String mandatoryScopes, ScopeAlgorithm calculateMinimalScopes) {

    public static final String PREFIX = OAuthProperties.PREFIX + ".scope-discovery";

    /**
     * Constructor with defaults for optional properties.
     */
    public ScopeProperties {
        if (scopeExtensions == null) {
            scopeExtensions = "";
        }
        if (mandatoryScopes == null) {
            mandatoryScopes = "";
        }
        if (calculateMinimalScopes == null) {
            calculateMinimalScopes = ScopeAlgorithm.NONE;
        }
    }

    public enum ScopeAlgorithm {
        NONE,
        GREEDY
    }
}
