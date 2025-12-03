package com.infobip.openapi.mcp.auth.scope;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test-http")
@TestPropertySource(
        properties = {"infobip.openapi.mcp.security.auth.oauth.scope-discovery.calculate-minimal-scopes=NONE"})
public class ScopeDiscoveryServiceTest extends OpenApiTestBase {

    @Autowired
    private ScopeDiscoveryService scopeDiscoveryService;

    @Autowired
    private ScopeProperties scopeProperties;

    @MockitoSpyBean
    protected OpenApiRegistry openApiRegistry;

    private static final String GIVEN_EXTENSIONS_OPENAPI_SPEC = """
            {
                "openapi": "3.1.0",
                "info": {"title": "Test API", "version": "1.0.0"},
                "paths": {
                    "/path1": {
                        "get": {
                            "x-scopes": ["read:resource1", "read:resource2"]
                        },
                        "post": {
                            "x-scopes": "write:resource1"
                        }
                    },
                    "/path2": {
                        "get": {
                            "x-scopes": ["read:resource3"]
                        },
                        "delete": {}
                    }
                }
            }
            """;

    private static final String GIVEN_SECURITY_OPENAPI_SPEC = """
            {
                "openapi": "3.1.0",
                "info": {"title": "Test API", "version": "1.0.0"},
                "paths": {
                    "/path1": {
                        "get": {
                            "security": [
                                {
                                    "oauth-sample": ["read:resource1", "read:resource2"]
                                }
                            ]
                        },
                        "post": {
                            "security": [
                                {
                                    "oauth-sample": [
                                        "write:resource1"
                                    ]
                                }
                            ]
                        }
                    },
                    "/path2": {
                        "get": {
                            "security": [
                                {
                                    "oauth-sample": ["read:resource3"]
                                }
                            ]
                        },
                        "delete": {}
                    }
                },
                "components": {
                    "securitySchemes": {
                        "oauth-sample": {
                            "type": "oauth2"
                        }
                    }
                }
            }
            """;

    @Test
    void testDefaultConfiguration() {
        // Then
        then(scopeProperties.enabled()).isTrue();
        then(scopeProperties.scopeExtensions()).isEmpty();
        then(scopeProperties.mandatoryScopes()).isEmpty();
        then(scopeProperties.calculateMinimalScopes()).isEqualTo(ScopeProperties.ScopeAlgorithm.NONE);
    }

    @Test
    void shouldHaveScopesLoadedOnBoot() {
        // When
        var actual = scopeDiscoveryService.getDiscoveredScopes();

        // Then
        then(actual).isNotNull();
        then(actual).isUnmodifiable();
    }

    protected void reloadOpenApi(String customOpenApiSpec) {
        setupCustomOpenAPIMock(staticWireMockServer, customOpenApiSpec);
        openApiRegistry.reload();
    }

    protected void reloadOpenApi(String customOpenApiSpec, OpenApiRegistry openApiRegistry) {
        setupCustomOpenAPIMock(staticWireMockServer, customOpenApiSpec);
        openApiRegistry.reload();
    }

    @Nested
    @TestPropertySource(
            properties = {"infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes"})
    class TestExtensionsDiscovery extends OpenApiTestBase {
        @Autowired
        private ScopeProperties scopeProperties;

        @Autowired
        private ScopeDiscoveryService scopeDiscoveryService;

        @Autowired
        private OpenApiRegistry openApiRegistry;

        @Test
        void shouldLoadCorrectProperties() {
            // Given
            var givenScopeDiscoveryEnabled = scopeProperties.enabled();
            var givenScopeExtensions = scopeProperties.scopeExtensions();
            var givenMandatoryScopes = scopeProperties.mandatoryScopes();
            var givenCalculateMinimalScopes = scopeProperties.calculateMinimalScopes();

            // Then
            then(givenScopeDiscoveryEnabled).isTrue();
            then(givenScopeExtensions).isEqualTo("x-scopes");
            then(givenMandatoryScopes).isEmpty();
            then(givenCalculateMinimalScopes).isEqualTo(ScopeProperties.ScopeAlgorithm.NONE);
        }

        @Test
        void shouldReturnEmptySet_whenNoScopesDefined() {
            // Given
            reloadOpenApi(getOpenAPISpec());

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).isEmpty();
        }

        @Test
        void shouldDiscoverScopesFromExtensions() {
            // Given
            reloadOpenApi(GIVEN_EXTENSIONS_OPENAPI_SPEC, openApiRegistry);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(4);
            then(result)
                    .containsExactlyInAnyOrder("read:resource1", "read:resource2", "read:resource3", "write:resource1");
        }
    }

    @Nested
    class TestSecurityDiscovery extends OpenApiTestBase {
        @Autowired
        private ScopeProperties scopeProperties;

        @Autowired
        private ScopeDiscoveryService scopeDiscoveryService;

        @Test
        void shouldLoadCorrectProperties() {
            // Given
            var givenScopeDiscoveryEnabled = scopeProperties.enabled();
            var givenScopeExtensions = scopeProperties.scopeExtensions();
            var givenMandatoryScopes = scopeProperties.mandatoryScopes();
            var givenCalculateMinimalScopes = scopeProperties.calculateMinimalScopes();

            // Then
            then(givenScopeDiscoveryEnabled).isTrue();
            then(givenScopeExtensions).isEmpty();
            then(givenMandatoryScopes).isEmpty();
            then(givenCalculateMinimalScopes).isEqualTo(ScopeProperties.ScopeAlgorithm.NONE);
        }

        @Test
        void shouldReturnEmptySet_whenNoScopesDefined() {
            // Given
            reloadOpenApi(getOpenAPISpec());

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).isEmpty();
        }

        @Test
        void shouldDiscoverScopesFromSecurity() {
            // Given
            reloadOpenApi(GIVEN_SECURITY_OPENAPI_SPEC);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(4);
            then(result)
                    .containsExactlyInAnyOrder("read:resource1", "read:resource2", "read:resource3", "write:resource1");
        }

        @Test
        void shouldDiscoverScopesFromSecurityIncludingGlobalWhenNoneEndpointsOverridden() {
            // Given
            var openApiSpecWithGlobalSecurity = """
                    {
                        "openapi": "3.1.0",
                        "info": {"title": "Test API", "version": "1.0.0"},
                        "security": [
                            {
                                "oauth-sample": ["read:resource1"]
                            }
                        ],
                        "paths": {
                            "/path1": {
                                "get": {}
                            }
                        },
                        "components": {
                            "securitySchemes": {
                                "oauth-sample": {
                                    "type": "oauth2"
                                }
                            }
                        }
                    }
                    """;
            reloadOpenApi(openApiSpecWithGlobalSecurity);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(1);
            then(result).containsExactly("read:resource1");
        }

        @Test
        void shouldDiscoverScopesFromSecurityWithoutGlobalWhenAllEndpointsOverridden() {
            // Given
            var openApiSpecWithGlobalSecurity = """
                    {
                        "openapi": "3.1.0",
                        "info": {"title": "Test API", "version": "1.0.0"},
                        "security": [
                            {
                                "oauth-sample": ["read:resource1"]
                            }
                        ],
                        "paths": {
                            "/path1": {
                                "get": {
                                    "security": [
                                        {
                                            "oauth-sample": ["write:resource1"]
                                        }
                                    ]
                                }
                            }
                        },
                        "components": {
                            "securitySchemes": {
                                "oauth-sample": {
                                    "type": "oauth2"
                                }
                            }
                        }
                    }
                    """;
            reloadOpenApi(openApiSpecWithGlobalSecurity);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(1);
            then(result).containsExactly("write:resource1");
        }

        @Test
        void shouldAllowForOptionalSecurityOnOperationLevel() {
            // Given
            var openApiSpecWithGlobalSecurity = """
                    {
                        "openapi": "3.1.0",
                        "info": {"title": "Test API", "version": "1.0.0"},
                        "security": [
                            {
                                "oauth-sample": ["read:resource1"]
                            }
                        ],
                        "paths": {
                            "/path1": {
                                "get": {
                                    "security": [
                                        {
                                            "oauth-sample": ["write:resource1"]
                                        },
                                        {}
                                    ]
                                }
                            },
                            "/path2": {
                                "get": {
                                    "security": [
                                        {}
                                    ]
                                }
                            }
                        },
                        "components": {
                            "securitySchemes": {
                                "oauth-sample": {
                                    "type": "oauth2"
                                }
                            }
                        }
                    }
                    """;
            reloadOpenApi(openApiSpecWithGlobalSecurity);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).isEmpty();
        }

        @Test
        void shouldAllowForOptionalSecurityOnGlobalLevel() {
            // Given
            var openApiSpecWithGlobalSecurity = """
                    {
                        "openapi": "3.1.0",
                        "info": {"title": "Test API", "version": "1.0.0"},
                        "security": [
                            {
                                "oauth-sample": ["read:resource1"]
                            },
                            {}
                        ],
                        "paths": {
                            "/path1": {
                                "get": {
                                }
                            }
                        },
                        "components": {
                            "securitySchemes": {
                                "oauth-sample": {
                                    "type": "oauth2"
                                }
                            }
                        }
                    }
                    """;
            reloadOpenApi(openApiSpecWithGlobalSecurity);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).isEmpty();
        }

        @Test
        void shouldNotUseSecurityRequirementValuesFromNonOauthBasedSchemes() {
            // Given
            var openApiSpecWithGlobalSecurity = """
                    {
                        "openapi": "3.1.0",
                        "info": {"title": "Test API", "version": "1.0.0"},
                        "security": [
                            {
                                "custom-apikey": ["global-level-requirement-value"]
                            }
                        ],
                        "paths": {
                            "/path1": {
                                "get": {
                                    "security": [
                                        {
                                            "custom-apikey": ["operation-level-requirement-value"]
                                        }
                                    ]
                                }
                            },
                            "/path2": {
                                "get": {
                                }
                            }
                        },
                        "components": {
                            "securitySchemes": {
                                "oauth-sample": {
                                    "type": "oauth2"
                                },
                                "custom-apikey": {
                                    "type": "apiKey"
                                }
                            }
                        }
                    }
                    """;
            reloadOpenApi(openApiSpecWithGlobalSecurity);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).isEmpty();
        }

        @Test
        void shouldDiscoverScopesFromSecurityIncludingGlobalWhenSomeEndpointsOverridden() {
            // Given
            var openApiSpecWithGlobalSecurity = """
                    {
                        "openapi": "3.1.0",
                        "info": {"title": "Test API", "version": "1.0.0"},
                        "security": [
                            {
                                "oauth-sample": ["read:resource1"]
                            }
                        ],
                        "paths": {
                            "/path1": {
                                "get": {
                                    "security": [
                                        {
                                            "oauth-sample": ["write:resource1"]
                                        }
                                    ]
                                },
                                "post": {}
                            }
                        },
                        "components": {
                            "securitySchemes": {
                                "oauth-sample": {
                                    "type": "oauth2"
                                }
                            }
                        }
                    }
                    """;
            reloadOpenApi(openApiSpecWithGlobalSecurity);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(2);
            then(result).containsExactlyInAnyOrder("read:resource1", "write:resource1");
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes",
                "infobip.openapi.mcp.security.auth.oauth.scope-discovery.mandatory-scopes=mandatory1,mandatory2"
            })
    class TestMandatoryScopes extends OpenApiTestBase {
        @Autowired
        private ScopeProperties scopeProperties;

        @Autowired
        private ScopeDiscoveryService scopeDiscoveryService;

        @Autowired
        private OpenApiRegistry openApiRegistry;

        @Test
        void shouldLoadCorrectProperties() {
            // Given
            var givenScopeDiscoveryEnabled = scopeProperties.enabled();
            var givenScopeExtensions = scopeProperties.scopeExtensions();
            var givenMandatoryScopes = scopeProperties.mandatoryScopes();
            var givenCalculateMinimalScopes = scopeProperties.calculateMinimalScopes();

            // Then
            then(givenScopeDiscoveryEnabled).isTrue();
            then(givenScopeExtensions).isEqualTo("x-scopes");
            then(givenMandatoryScopes).isEqualTo("mandatory1,mandatory2");
            then(givenCalculateMinimalScopes).isEqualTo(ScopeProperties.ScopeAlgorithm.NONE);
        }

        @Test
        void shouldReturnMandatoryScopes_whenNoScopesDefined() {
            // Given
            reloadOpenApi(getOpenAPISpec());

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(2);
            then(result).containsExactlyInAnyOrder("mandatory1", "mandatory2");
        }

        @Test
        void shouldDiscoverScopesFromExtensions() {
            // Given
            reloadOpenApi(GIVEN_EXTENSIONS_OPENAPI_SPEC, openApiRegistry);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(6);
            then(result)
                    .containsExactlyInAnyOrder(
                            "read:resource1",
                            "read:resource2",
                            "read:resource3",
                            "write:resource1",
                            "mandatory1",
                            "mandatory2");
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes",
                "infobip.openapi.mcp.security.auth.oauth.scope-discovery.calculate-minimal-scopes=GREEDY"
            })
    class TestMinimalScopeCalculation extends OpenApiTestBase {
        @Autowired
        private ScopeProperties scopeProperties;

        @Autowired
        private ScopeDiscoveryService scopeDiscoveryService;

        @Autowired
        private OpenApiRegistry openApiRegistry;

        @Test
        void shouldLoadCorrectProperties() {
            // Given
            var givenScopeDiscoveryEnabled = scopeProperties.enabled();
            var givenScopeExtensions = scopeProperties.scopeExtensions();
            var givenMandatoryScopes = scopeProperties.mandatoryScopes();
            var givenCalculateMinimalScopes = scopeProperties.calculateMinimalScopes();

            // Then
            then(givenScopeDiscoveryEnabled).isTrue();
            then(givenScopeExtensions).isEqualTo("x-scopes");
            then(givenMandatoryScopes).isEmpty();
            then(givenCalculateMinimalScopes).isEqualTo(ScopeProperties.ScopeAlgorithm.GREEDY);
        }

        @Test
        void shouldReturnEmptySet_whenNoScopesDefined() {
            // Given
            reloadOpenApi(getOpenAPISpec());

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).isEmpty();
        }

        @Test
        void shouldDiscoverScopesFromExtensions() {
            // Given
            reloadOpenApi(GIVEN_EXTENSIONS_OPENAPI_SPEC, openApiRegistry);

            // When
            var result = scopeDiscoveryService.discover();

            // Then
            then(result).isNotNull();
            then(result).hasSize(3);
            then(result).containsExactlyInAnyOrder("read:resource1", "read:resource3", "write:resource1");
        }
    }
}
