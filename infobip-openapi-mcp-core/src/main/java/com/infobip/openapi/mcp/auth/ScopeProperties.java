package com.infobip.openapi.mcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Scope discovery properties for OpenAPI MCP Server.
 *
 * @param enabled                Enable OAuth scope discovery. Default is false.
 * @param scopeExtensions        Scope extensions to read scopes from. Default is an empty string.
 * @param mandatoryScopes        Mandatory scopes that must be present. Scopes should be comma-separated.
 *                               Default is an empty string.
 * @param calculateMinimalScopes Algorithm for calculating the minimal set of scopes that can access all API endpoints.
 *                              Two values are supported: NONE, which skips calculation and requests all discovered
 *                               scopes, and GREEDY which uses a greedy algorithm to find a smaller set of scopes that
 *                               sill covers all operations.
 *                               Default is NONE.
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
        NONE, GREEDY
    }
}
