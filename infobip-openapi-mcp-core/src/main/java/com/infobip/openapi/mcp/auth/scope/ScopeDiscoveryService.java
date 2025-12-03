package com.infobip.openapi.mcp.auth.scope;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

public class ScopeDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeDiscoveryService.class);

    private final ScopeProperties scopeProperties;
    private final OpenApiRegistry openApiRegistry;
    private final MinimalSetCalculator minimalSetCalculator;

    private Set<String> discoveredScopes = null;

    public ScopeDiscoveryService(
            ScopeProperties scopeProperties,
            OpenApiRegistry openApiRegistry,
            MinimalSetCalculator minimalSetCalculator) {
        this.scopeProperties = scopeProperties;
        this.openApiRegistry = openApiRegistry;
        this.minimalSetCalculator = minimalSetCalculator;
    }

    public Set<String> getDiscoveredScopes() {
        if (discoveredScopes == null) {
            discover();
        }
        return Collections.unmodifiableSet(discoveredScopes);
    }

    public Set<String> discover() {
        var discoveredScopesList = discoverScopes(openApiRegistry.openApi());
        var scopeSet = calculateScopeSet(discoveredScopesList);

        if (!scopeProperties.mandatoryScopes().isEmpty()) {
            scopeSet.addAll(
                    Arrays.stream(scopeProperties.mandatoryScopes().split(",")).toList());
        }

        var discoverScopesSet = new HashSet<>(scopeSet);
        this.discoveredScopes = discoverScopesSet;

        LOGGER.info("Discovered scopes: {}", discoverScopesSet);

        return discoverScopesSet;
    }

    private List<List<String>> discoverScopesFromExtensions(OpenAPI openApi) {
        var discoveredScopes = new ArrayList<List<String>>();
        for (var pathEntry : openApi.getPaths().entrySet()) {
            for (var operationEntry : pathEntry.getValue().readOperationsMap().entrySet()) {
                var operation = operationEntry.getValue();
                if (operation.getExtensions() != null
                        && operation.getExtensions().containsKey(scopeProperties.scopeExtensions())) {
                    var extensionValue = operation.getExtensions().get(scopeProperties.scopeExtensions());
                    if (extensionValue instanceof List<?> scopes) {
                        var scopeList = scopes.stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .toList();
                        if (!scopeList.isEmpty()) {
                            discoveredScopes.add(scopeList);
                        }
                    } else if (extensionValue instanceof String scope) {
                        if (!scope.isBlank()) {
                            discoveredScopes.add(List.of(scope));
                        }
                    }
                }
            }
        }
        return discoveredScopes;
    }

    private List<List<String>> discoverScopesFromSecurity(OpenAPI openApi) {
        var namesOfSchemesThatUseScopes = new HashSet<>();
        var securitySchemes = Optional.of(openApi)
                .map(OpenAPI::getComponents)
                .map(Components::getSecuritySchemes)
                .map(Map::entrySet)
                .orElse(Set.of());
        for (var securitySchemeEntry : securitySchemes) {
            var type = securitySchemeEntry.getValue().getType();
            // OpenID connect is built on top of OAuth2, both schemes use scopes.
            if (type == SecurityScheme.Type.OAUTH2 || type == SecurityScheme.Type.OPENIDCONNECT) {
                namesOfSchemesThatUseScopes.add(securitySchemeEntry.getKey());
            }
        }

        var globalScopes = extractScopes(namesOfSchemesThatUseScopes, openApi.getSecurity());
        var globalScopesUsed = false;

        var discoveredScopes = new ArrayList<List<String>>();
        for (var pathEntry : openApi.getPaths().entrySet()) {
            for (var operationEntry : pathEntry.getValue().readOperationsMap().entrySet()) {
                var operation = operationEntry.getValue();
                if (operation.getSecurity() != null) {
                    discoveredScopes.addAll(extractScopes(namesOfSchemesThatUseScopes, operation.getSecurity()));
                } else {
                    globalScopesUsed = true;
                }
            }
        }

        if (globalScopesUsed) {
            discoveredScopes.addAll(globalScopes);
        }

        return discoveredScopes;
    }

    private List<? extends List<String>> extractScopes(
            HashSet<?> namesOfSchemesThatUseScopes, List<SecurityRequirement> securityRequirements) {
        if (securityRequirements == null) {
            return List.of();
        }

        var result = new ArrayList<List<String>>();
        for (var securityRequirement : securityRequirements) {
            if (securityRequirement.isEmpty()) {
                // Empty security requirement signals that security requirements
                // are optional. In this case we shouldn't use any scopes.
                return List.of();
            }

            for (var requirementEntry : securityRequirement.entrySet()) {
                var securitySchemeName = requirementEntry.getKey();
                var requirementValues = requirementEntry.getValue();
                var schemeUsesScopes = namesOfSchemesThatUseScopes.contains(securitySchemeName);
                var requirementHasValues = requirementValues != null && !requirementValues.isEmpty();
                if (schemeUsesScopes && requirementHasValues) {
                    result.add(requirementValues);
                }
            }
        }
        return result;
    }

    private List<List<String>> discoverScopes(OpenAPI openApi) {
        if (scopeProperties.scopeExtensions().isEmpty()) {
            return discoverScopesFromSecurity(openApi);
        }
        return discoverScopesFromExtensions(openApi);
    }

    private Set<String> calculateScopeSet(List<List<String>> discoveredScopes) {
        if (scopeProperties.calculateMinimalScopes() != ScopeProperties.ScopeAlgorithm.NONE) {
            return minimalSetCalculator.calculateMinimalScopes(
                    discoveredScopes, scopeProperties.calculateMinimalScopes());
        }
        return calculateUnionSet(discoveredScopes);
    }

    private Set<String> calculateUnionSet(List<List<String>> discoveredScopes) {
        return discoveredScopes.stream().flatMap(List::stream).collect(Collectors.toSet());
    }

    @EventListener(ContextRefreshedEvent.class)
    private void onApplicationReady() {
        this.discover();
    }
}
