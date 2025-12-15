package com.infobip.openapi.mcp.auth.scope;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtScopeService {

    public static final Logger LOGGER = LoggerFactory.getLogger(JwtScopeService.class);

    private final ScopeDiscoveryService scopeDiscoveryService;

    public JwtScopeService(ScopeDiscoveryService scopeDiscoveryService) {
        this.scopeDiscoveryService = scopeDiscoveryService;
    }

    public JWTClaimsSet decodeJwtToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        var token = authHeader.substring(7).trim();
        try {
            return JWTParser.parse(token).getJWTClaimsSet();
        } catch (Exception e) {
            return null;
        }
    }

    public Set<String> extractScopes(JWTClaimsSet claims) {
        var scopes = new HashSet<String>();

        try {
            var scopeString = claims.getStringClaim("scope");
            if (scopeString != null && !scopeString.isBlank()) {
                scopes.addAll(Arrays.asList(scopeString.split(" ")));
            }
        } catch (ParseException e) {
            LOGGER.debug("No 'scope' claim found in JWT token.", e);
        }

        return scopes;
    }

    public boolean verifyScopes(Set<String> tokenScopes) {
        var requiredScopes = scopeDiscoveryService.getDiscoveredScopes();
        return tokenScopes.containsAll(requiredScopes);
    }
}
