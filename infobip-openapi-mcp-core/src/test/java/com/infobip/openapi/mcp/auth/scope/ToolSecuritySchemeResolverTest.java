package com.infobip.openapi.mcp.auth.scope;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolSecuritySchemeResolverTest {

    private static final String OAUTH_SCHEME = "oauthScheme";

    @Nested
    class SecurityDeclaration {

        @Test
        void shouldAdvertiseAnonymousOptionForSilentSpecification() {
            var advertisement = resolve(openApi(null), operation(null));

            then(advertisement).contains(List.of(Map.of("type", "noauth")));
        }

        @Test
        void shouldAdvertiseAnonymousOptionForEmptySecurityRequirement() {
            var advertisement = resolve(openApi(List.of(new SecurityRequirement())), operation(null));

            then(advertisement).contains(List.of(Map.of("type", "noauth")));
        }

        @Test
        void shouldAdvertiseInheritedGlobalScopesWithoutAnonymousOption() {
            var openApi = openApi(List.of(requirement(OAUTH_SCHEME, "zalo:manage", "zalo:read")));

            var advertisement = resolve(openApi, operation(null));

            then(advertisement)
                    .contains(List.of(Map.of("type", "oauth2", "scopes", List.of("zalo:manage", "zalo:read"))));
        }

        @Test
        void shouldAdvertiseOperationScopesOverInheritedGlobalOnes() {
            var openApi = openApi(List.of(requirement(OAUTH_SCHEME, "zalo:manage", "zalo:read")));
            var operation = operation(List.of(requirement(OAUTH_SCHEME, "zalo:read")));

            var advertisement = resolve(openApi, operation);

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of("zalo:read"))));
        }

        @Test
        void shouldAdvertiseBothOptionsForEmptyRequirementCombinedWithOAuth() {
            var operation = operation(List.of(new SecurityRequirement(), requirement(OAUTH_SCHEME, "zalo:read")));

            var advertisement = resolve(openApi(List.of(requirement(OAUTH_SCHEME, "zalo:manage"))), operation);

            then(advertisement)
                    .contains(List.of(
                            Map.of("type", "noauth"), Map.of("type", "oauth2", "scopes", List.of("zalo:read"))));
        }

        @Test
        void shouldKeepScopeListPresentButEmptyWhenSchemeIsReferencedWithoutValues() {
            var operation = operation(List.of(new SecurityRequirement().addList(OAUTH_SCHEME, List.of())));

            var advertisement = resolve(openApi(null), operation);

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of())));
        }

        @Test
        void shouldTolerateNullScopeValuesInRequirement() {
            var operation = operation(List.of(new SecurityRequirement().addList(OAUTH_SCHEME, (List<String>) null)));

            var advertisement = resolve(openApi(null), operation);

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of())));
        }

        @Test
        void shouldUnionScopesAcrossAlternativeRequirements() {
            var operation = operation(
                    List.of(requirement(OAUTH_SCHEME, "zalo:read"), requirement(OAUTH_SCHEME, "zalo:manage")));

            var advertisement = resolve(openApi(null), operation);

            then(advertisement)
                    .contains(List.of(Map.of("type", "oauth2", "scopes", List.of("zalo:manage", "zalo:read"))));
        }

        @Test
        void shouldAdvertiseOpenIdConnectSchemeAsOAuth2() {
            var openApi = openApi(
                    List.of(requirement("oidcScheme", "openid")), "oidcScheme", SecurityScheme.Type.OPENIDCONNECT);

            var advertisement = resolve(openApi, operation(null));

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of("openid"))));
        }

        @Test
        void shouldNotAdvertiseOperationSecuredOnlyByNonScopeSchemes() {
            var openApi = openApi(
                    List.of(new SecurityRequirement().addList("apiKeyScheme", List.of())),
                    "apiKeyScheme",
                    SecurityScheme.Type.APIKEY);

            var advertisement = resolve(openApi, operation(null));

            then(advertisement).isEmpty();
        }

        @Test
        void shouldAdvertiseMandatoryScopesAsOAuth2OptionForOperationSecuredOnlyByNonScopeSchemes() {
            var openApi = openApi(
                    List.of(new SecurityRequirement().addList("apiKeyScheme", List.of())),
                    "apiKeyScheme",
                    SecurityScheme.Type.APIKEY);
            var resolver = new ToolSecuritySchemeResolver(properties("", "required_scope"));

            var advertisement = resolver.resolve(openApi, operation(null));

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of("required_scope"))));
        }
    }

    @Nested
    class MandatoryScopes {

        @Test
        void shouldOfferOAuth2AlongsideAnonymousForSilentSpecification() {
            var resolver = new ToolSecuritySchemeResolver(properties("", "required_scope"));

            var advertisement = resolver.resolve(openApi(null), operation(null));

            then(advertisement)
                    .contains(List.of(
                            Map.of("type", "noauth"), Map.of("type", "oauth2", "scopes", List.of("required_scope"))));
        }

        @Test
        void shouldMergeMandatoryScopesIntoOperationScopes() {
            var resolver = new ToolSecuritySchemeResolver(properties("", "required_scope"));

            var advertisement =
                    resolver.resolve(openApi(List.of(requirement(OAUTH_SCHEME, "zalo:read"))), operation(null));

            then(advertisement)
                    .contains(List.of(Map.of("type", "oauth2", "scopes", List.of("required_scope", "zalo:read"))));
        }

        @Test
        void shouldSplitCommaSeparatedMandatoryScopes() {
            var resolver = new ToolSecuritySchemeResolver(properties("", "required_scope,offline_access"));

            var advertisement = resolver.resolve(openApi(null), operation(null));

            then(advertisement).get().satisfies(schemes -> then(schemes.get(1))
                    .containsEntry("scopes", List.of("offline_access", "required_scope")));
        }
    }

    @Nested
    class ScopeExtensions {

        @Test
        void shouldAdvertiseScopesFromExtensionList() {
            var resolver = new ToolSecuritySchemeResolver(properties("x-scopes", ""));
            var operation = operationWithExtension("x-scopes", List.of("cci:read", "cci:write"));

            var advertisement = resolver.resolve(openApi(null), operation);

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of("cci:read", "cci:write"))));
        }

        @Test
        void shouldAdvertiseScopesFromExtensionString() {
            var resolver = new ToolSecuritySchemeResolver(properties("x-scopes", ""));
            var operation = operationWithExtension("x-scopes", "cci:read");

            var advertisement = resolver.resolve(openApi(null), operation);

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of("cci:read"))));
        }

        @Test
        void shouldIgnoreBlankAndNullEntriesInExtensionList() {
            var resolver = new ToolSecuritySchemeResolver(properties("x-scopes", ""));
            var scopes = new ArrayList<Object>();
            scopes.add("cci:read");
            scopes.add(null);
            scopes.add(" ");
            var operation = operationWithExtension("x-scopes", scopes);

            var advertisement = resolver.resolve(openApi(null), operation);

            then(advertisement).contains(List.of(Map.of("type", "oauth2", "scopes", List.of("cci:read"))));
        }

        @Test
        void shouldAdvertiseAnonymousOptionForEmptyExtensionList() {
            var resolver = new ToolSecuritySchemeResolver(properties("x-scopes", ""));
            var operation = operationWithExtension("x-scopes", List.of());

            var advertisement = resolver.resolve(openApi(null), operation);

            then(advertisement).contains(List.of(Map.of("type", "noauth")));
        }

        @Test
        void shouldAdvertiseAnonymousOptionWithoutExtension() {
            var resolver = new ToolSecuritySchemeResolver(properties("x-scopes", ""));

            var advertisement = resolver.resolve(openApi(null), operation(null));

            then(advertisement).contains(List.of(Map.of("type", "noauth")));
        }

        @Test
        void shouldMergeMandatoryScopesIntoExtensionScopes() {
            var resolver = new ToolSecuritySchemeResolver(properties("x-scopes", "required_scope"));
            var operation = operationWithExtension("x-scopes", "cci:read");

            var advertisement = resolver.resolve(openApi(null), operation);

            then(advertisement)
                    .contains(List.of(Map.of("type", "oauth2", "scopes", List.of("cci:read", "required_scope"))));
        }
    }

    private Optional<List<Map<String, Object>>> resolve(OpenAPI openApi, Operation operation) {
        return new ToolSecuritySchemeResolver(properties("", "")).resolve(openApi, operation);
    }

    private static ScopeProperties properties(String scopeExtensions, String mandatoryScopes) {
        return new ScopeProperties(true, scopeExtensions, mandatoryScopes, null);
    }

    private static OpenAPI openApi(List<SecurityRequirement> security) {
        return openApi(security, OAUTH_SCHEME, SecurityScheme.Type.OAUTH2);
    }

    private static OpenAPI openApi(
            List<SecurityRequirement> security, String schemeName, SecurityScheme.Type schemeType) {
        var openApi = new OpenAPI();
        openApi.setSecurity(security);
        openApi.setComponents(
                new Components().securitySchemes(Map.of(schemeName, new SecurityScheme().type(schemeType))));
        return openApi;
    }

    private static Operation operation(@Nullable List<SecurityRequirement> security) {
        var operation = new Operation();
        operation.setSecurity(security);
        return operation;
    }

    private static Operation operationWithExtension(String key, Object value) {
        var operation = new Operation();
        var extensions = new HashMap<String, Object>();
        extensions.put(key, value);
        operation.setExtensions(extensions);
        return operation;
    }

    private static SecurityRequirement requirement(String schemeName, String... scopes) {
        return new SecurityRequirement().addList(schemeName, List.of(scopes));
    }
}
