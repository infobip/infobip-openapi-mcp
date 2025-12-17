package com.infobip.openapi.mcp.auth.scope;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for prechecking JWT token scopes before forwarding to the authorization server.
 * <p>
 * This service extracts and validates scopes from JWT tokens but does <b>not</b> verify
 * user identity or token signatures. Its sole purpose is to precheck that the required
 * scopes are present in the token before making external authorization requests.
 * </p>
 *
 * <h3>Scope Extraction:</h3>
 * <ul>
 *   <li>Decodes JWT tokens from Bearer authorization headers</li>
 *   <li>Extracts the "scope" claim as space-separated values</li>
 *   <li>Validates presence of all required scopes from {@link ScopeDiscoveryService}</li>
 * </ul>
 *
 * <h3>Security Note:</h3>
 * <p>
 * This is a <b>precheck optimization</b>, not a security boundary. Token validation
 * and user authentication are handled by the external authorization server.
 * </p>
 *
 * @see ScopeDiscoveryService
 * @see com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter
 */
public class JwtScopeService {

    public static final Logger LOGGER = LoggerFactory.getLogger(JwtScopeService.class);

    private final ScopeDiscoveryService scopeDiscoveryService;

    public JwtScopeService(ScopeDiscoveryService scopeDiscoveryService) {
        this.scopeDiscoveryService = scopeDiscoveryService;
    }

    /**
     * Verifies that the token from authorization header is jwt token and contains all required scopes.
     * Tokens must be prefixed with "Bearer". Bearer prefix is case-insensitive and leading/trailing
     * whitespace around the token is ignored. Uses the "scope" claim, which is expected to be a
     * space-separated string as per RFC 6749.
     *
     * @param authHeader The Authorization header value
     * @return True if all required scopes are present, false otherwise
     */
    public boolean verifyScopesFromHeader(String authHeader) {
        var tokenScopes = decodeJwtTokenAndExtractScopes(authHeader);
        var requiredScopes = scopeDiscoveryService.getDiscoveredScopes();
        return tokenScopes.containsAll(requiredScopes);
    }

    /**
     * Decodes a JWT token from the Authorization header. Tokens must be prefixed with "Bearer".
     * Bearer prefix is case-insensitive and leading/trailing whitespace around the token is ignored.
     *
     * @param authHeader The Authorization header value
     * @return A set of scopes extracted from the token, or an empty set if decoding fails
     */
    Set<String> decodeJwtTokenAndExtractScopes(String authHeader) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Set.of();
        }

        var token = authHeader.substring(7).trim();
        try {
            var claimsSet = JWTParser.parse(token).getJWTClaimsSet();
            return extractScopes(claimsSet);
        } catch (ParseException e) {
            LOGGER.debug("No 'scope' claim found in JWT token.", e);
        }
        return Set.of();
    }

    /**
     * Extracts scopes from the JWT claims.
     * Uses the "scope" claim, which is expected to be a space-separated string as per RFC 6749.
     *
     * @param claims The JWT claims
     * @return A set of scopes extracted from the "scope" claim
     */
    private Set<String> extractScopes(@Nullable JWTClaimsSet claims) throws ParseException {
        if (claims == null) {
            return Set.of();
        }

        var scopeString = claims.getStringClaim("scope");
        if (scopeString != null && !scopeString.isBlank()) {
            return new HashSet<>(Arrays.asList(scopeString.split(" ")));
        }

        return Set.of();
    }
}
