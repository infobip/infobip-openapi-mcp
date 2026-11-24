package com.infobip.openapi.mcp.auth.scope;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the per-tool {@code _meta.securitySchemes} advertisement from the operation's own security
 * declarations: {@code noauth} when its effective security permits anonymous calls, {@code oauth2} when it
 * references a scope-bearing scheme or the deployment configures mandatory scopes.
 */
public class ToolSecuritySchemeResolver {

    private final ScopeProperties scopeProperties;

    public ToolSecuritySchemeResolver(ScopeProperties scopeProperties) {
        this.scopeProperties = scopeProperties;
    }

    public Optional<List<Map<String, Object>>> resolve(OpenAPI openApi, Operation operation) {
        var scopeExtensions = scopeProperties.scopeExtensions();
        if (!scopeExtensions.isEmpty()) {
            var declaredScopes = declaredExtensionScopes(operation, scopeExtensions);
            return declaredScopes.isEmpty() ? advertisement(true, null) : advertisement(false, declaredScopes);
        }
        return resolveFromSecurity(openApi, operation);
    }

    private Optional<List<Map<String, Object>>> resolveFromSecurity(OpenAPI openApi, Operation operation) {
        var scopeSchemeNames = namesOfSchemesUsingScopes(openApi);
        var effectiveSecurity = operation.getSecurity() != null ? operation.getSecurity() : openApi.getSecurity();
        if (effectiveSecurity == null || effectiveSecurity.isEmpty()) {
            return advertisement(true, null);
        }

        var anonymousAllowed = false;
        var oauthReferenced = false;
        var scopes = new LinkedHashSet<String>();
        for (var requirement : effectiveSecurity) {
            if (requirement.isEmpty()) {
                anonymousAllowed = true;
                continue;
            }
            for (var entry : requirement.entrySet()) {
                if (scopeSchemeNames.contains(entry.getKey())) {
                    oauthReferenced = true;
                    if (entry.getValue() != null) {
                        scopes.addAll(entry.getValue());
                    }
                }
            }
        }
        return advertisement(anonymousAllowed, oauthReferenced ? scopes : null);
    }

    /**
     * Assembles the advertisement; a {@code null} scopes argument means the operation references no
     * scope-bearing scheme, while mandatory scopes still make OAuth2 an option.
     */
    private Optional<List<Map<String, Object>>> advertisement(
            boolean anonymousAllowed, @Nullable Collection<String> referencedScopes) {
        var mandatoryScopes = mandatoryScopes();
        var oauthAdvertised = referencedScopes != null || !mandatoryScopes.isEmpty();
        if (!anonymousAllowed && !oauthAdvertised) {
            return Optional.empty();
        }

        var schemes = new ArrayList<Map<String, Object>>();
        if (anonymousAllowed) {
            schemes.add(Map.of("type", "noauth"));
        }
        if (oauthAdvertised) {
            var scopes = new LinkedHashSet<String>();
            if (referencedScopes != null) {
                scopes.addAll(referencedScopes);
            }
            scopes.addAll(mandatoryScopes);
            var oauth2 = new LinkedHashMap<String, Object>();
            oauth2.put("type", "oauth2");
            oauth2.put("scopes", scopes.stream().sorted().toList());
            schemes.add(oauth2);
        }
        return Optional.of(List.copyOf(schemes));
    }

    private List<String> mandatoryScopes() {
        var mandatory = scopeProperties.mandatoryScopes();
        return mandatory.isEmpty()
                ? List.of()
                : Arrays.stream(mandatory.split(",")).toList();
    }

    private Set<String> namesOfSchemesUsingScopes(OpenAPI openApi) {
        var securitySchemes = Optional.of(openApi)
                .map(OpenAPI::getComponents)
                .map(Components::getSecuritySchemes)
                .map(Map::entrySet)
                .orElse(Set.of());

        var names = new HashSet<String>();
        for (var securitySchemeEntry : securitySchemes) {
            var type = securitySchemeEntry.getValue().getType();
            // OpenID connect is built on top of OAuth2, both scheme types use scopes.
            if (type == SecurityScheme.Type.OAUTH2 || type == SecurityScheme.Type.OPENIDCONNECT) {
                names.add(securitySchemeEntry.getKey());
            }
        }
        return names;
    }

    private List<String> declaredExtensionScopes(Operation operation, String scopeExtensions) {
        var extensions = operation.getExtensions();
        if (extensions == null) {
            return List.of();
        }

        var extensionValue = extensions.get(scopeExtensions);
        if (extensionValue instanceof List<?> scopes) {
            return scopes.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(scope -> !scope.isBlank())
                    .toList();
        }
        if (extensionValue instanceof String scope && !scope.isBlank()) {
            return List.of(scope);
        }
        return List.of();
    }
}
