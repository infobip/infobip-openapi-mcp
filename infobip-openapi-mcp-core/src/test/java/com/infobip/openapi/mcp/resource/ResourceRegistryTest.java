package com.infobip.openapi.mcp.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ResourceRegistryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final McpRequestContext CONTEXT = new McpRequestContext();

    @Mock
    private OpenApiRegistry openApiRegistry;

    private WireMockServer wireMockServer;
    private RestClient restClient;
    private final CredentialProvider noOpCredentialProvider = context -> Optional.empty();
    private final ApiRequestEnricherChain noOpEnricherChain = new ApiRequestEnricherChain(List.of());
    private final MetricService metricService = new NoOpMetricService();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0));
        wireMockServer.start();
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Nested
    class EmptyExtension {

        @Test
        void shouldReturnEmptyListWhenNoExtensions() {
            // Given
            var registry = givenRegistryWithExtension(null);

            // When
            var resources = registry.getResources();

            // Then
            then(resources).isEmpty();
        }

        @Test
        void shouldReturnEmptyListWhenNoResourcesExtension() {
            // Given
            var openApi = new OpenAPI();
            openApi.addExtension("x-other", "value");
            when(openApiRegistry.openApi()).thenReturn(openApi);
            var registry = new ResourceRegistry(
                    openApiRegistry,
                    restClient,
                    OBJECT_MAPPER,
                    noOpCredentialProvider,
                    noOpEnricherChain,
                    metricService);

            // When
            var resources = registry.getResources();

            // Then
            then(resources).isEmpty();
        }
    }

    @Nested
    class InlineContent {

        @Test
        void shouldRegisterInlineConcreteResourceWithText() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://greeting",
                    "name", "greeting",
                    "description", "A greeting",
                    "mimeType", "text/plain",
                    "inline", Map.of("text", "Hello, world!"))));

            // When
            var resources = registry.getResources();

            // Then
            then(resources).hasSize(1);
            var resource = resources.getFirst();
            then(resource.isTemplate()).isFalse();
            then(resource.resource().uri()).isEqualTo("res://greeting");
            then(resource.resource().name()).isEqualTo("greeting");
            then(resource.resource().mimeType()).isEqualTo("text/plain");

            var result = resource.handler().apply(CONTEXT, new McpSchema.ReadResourceRequest("res://greeting", null));
            then(result.contents()).hasSize(1);
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.text()).isEqualTo("Hello, world!");
            then(contents.mimeType()).isEqualTo("text/plain");
        }

        @Test
        void shouldRegisterInlineConcreteResourceWithBlob() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://image",
                    "name", "image",
                    "mimeType", "image/png",
                    "inline", Map.of("blob", "aGVsbG8="))));

            var resource = registry.getResources().getFirst();

            // When
            var result = resource.handler().apply(CONTEXT, new McpSchema.ReadResourceRequest("res://image", null));

            // Then
            var contents = (McpSchema.BlobResourceContents) result.contents().getFirst();
            then(contents.blob()).isEqualTo("aGVsbG8=");
            then(contents.mimeType()).isEqualTo("image/png");
        }

        @Test
        void shouldRegisterInlineResourceTemplate() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uriTemplate", "res://greet/{name}",
                    "name", "greet-template",
                    "inline", Map.of("text", "Static template content"))));

            // When
            var resources = registry.getResources();

            // Then
            then(resources).hasSize(1);
            var resource = resources.getFirst();
            then(resource.isTemplate()).isTrue();
            then(resource.template().uriTemplate()).isEqualTo("res://greet/{name}");

            var result =
                    resource.handler().apply(CONTEXT, new McpSchema.ReadResourceRequest("res://greet/Alice", null));
            then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                    .isEqualTo("Static template content");
        }
    }

    @Nested
    class ResolvedBackendResolution {

        @Test
        void shouldRegisterResolvedConcreteResource() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://simple",
                    "name", "simple-resource",
                    "description", "A simple resource",
                    "resolve", Map.of("path", "/resources/simple"))));

            // When
            var resources = registry.getResources();

            // Then
            then(resources).hasSize(1);
            var resource = resources.getFirst();
            then(resource.resource().uri()).isEqualTo("res://simple");
            then(resource.resource().description()).isEqualTo("A simple resource");
        }

        @Test
        void shouldResolveConcreteResourceViaGet() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/greeting")).willReturn(okJson("\"Hello, world!\"")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://greeting",
                    "name", "greeting",
                    "mimeType", "application/json",
                    "resolve", Map.of("path", "/resources/greeting"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://greeting", null);

            // When
            var result = resource.handler().apply(CONTEXT, request);

            // Then
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.text()).isEqualTo("\"Hello, world!\"");
            then(contents.mimeType()).isEqualTo("application/json");
            then(contents.uri()).isEqualTo("res://greeting");
        }

        @Test
        void shouldResolveResourceTemplateAndForwardVariablesAsQueryParams() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/greet"))
                    .withQueryParam("name", equalTo("Alice"))
                    .willReturn(ok("Hello Alice!")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uriTemplate", "res://greet/{name}",
                    "name", "greet-template",
                    "resolve", Map.of("path", "/resources/greet"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://greet/Alice", null);

            // When
            var result = resource.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                    .isEqualTo("Hello Alice!");
        }

        @Test
        void shouldResolveResourceTemplateAndSubstituteVariableIntoPath() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/greet/Alice")).willReturn(ok("Hello Alice!")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uriTemplate", "res://greet/{name}",
                    "name", "greet-template",
                    "resolve", Map.of("path", "/resources/greet/{name}"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://greet/Alice", null);

            // When
            var result = resource.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                    .isEqualTo("Hello Alice!");
        }

        @Test
        void shouldSubstitutePathVariableAndForwardRemainingVariableAsQueryParam() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/users/42/posts"))
                    .withQueryParam("filter", equalTo("recent"))
                    .willReturn(ok("Posts for user 42")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uriTemplate", "res://users/{userId}/posts/{filter}",
                    "name", "user-posts",
                    "resolve", Map.of("path", "/resources/users/{userId}/posts"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://users/42/posts/recent", null);

            // When
            var result = resource.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                    .isEqualTo("Posts for user 42");
        }

        @Test
        void shouldFallBackToResponseContentTypeWhenMimeTypeNotDefined() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/greeting"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/csv")
                            .withBody("a,b,c")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://greeting",
                    "name", "greeting",
                    "resolve", Map.of("path", "/resources/greeting"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://greeting", null);

            // When
            var result = resource.handler().apply(CONTEXT, request);

            // Then
            var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
            then(contents.text()).isEqualTo("a,b,c");
            then(contents.mimeType()).isEqualTo("text/csv");
        }

        @Test
        void shouldForwardCredentialsInAuthorizationHeader() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/secure"))
                    .withHeader("Authorization", equalTo("Bearer test-token"))
                    .willReturn(ok("Secret data.")));

            CredentialProvider credentialProvider = context -> Optional.of("Bearer test-token");
            var registry = givenRegistryWithExtension(
                    List.of(Map.of(
                            "uri", "res://secure",
                            "name", "secure",
                            "resolve", Map.of("path", "/resources/secure"))),
                    credentialProvider);

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://secure", null);

            // When
            var result = resource.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                    .isEqualTo("Secret data.");
            wireMockServer.verify(getRequestedFor(urlPathEqualTo("/resources/secure"))
                    .withHeader("Authorization", equalTo("Bearer test-token")));
        }

        @Test
        void shouldThrowWhenBackendReturnsError() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/resources/failing"))
                    .willReturn(serverError().withBody("Internal error")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://failing",
                    "name", "failing",
                    "resolve", Map.of("path", "/resources/failing"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://failing", null);

            // When / Then
            thenThrownBy(() -> resource.handler().apply(CONTEXT, request))
                    .isInstanceOf(ResourceExecutionException.class)
                    .hasMessageContaining("res://failing")
                    .hasMessageContaining("/resources/failing");
        }

        @Test
        void shouldThrowWhenBackendUnreachable() {
            // Given
            var unreachableRestClient = RestClient.builder()
                    .baseUrl("http://localhost:19999")
                    .requestFactory(new SimpleClientHttpRequestFactory())
                    .build();

            var openApi = new OpenAPI();
            openApi.addExtension(
                    "x-mcp-resources",
                    List.of(Map.of(
                            "uri", "res://unreachable",
                            "name", "unreachable",
                            "resolve", Map.of("path", "/resources/unreachable"))));
            when(openApiRegistry.openApi()).thenReturn(openApi);
            var registry = new ResourceRegistry(
                    openApiRegistry,
                    unreachableRestClient,
                    OBJECT_MAPPER,
                    noOpCredentialProvider,
                    noOpEnricherChain,
                    metricService);

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://unreachable", null);

            // When / Then
            thenThrownBy(() -> resource.handler().apply(CONTEXT, request))
                    .isInstanceOf(ResourceExecutionException.class)
                    .hasMessageContaining("res://unreachable");
        }

        @Test
        void shouldResolveResourceViaAbsoluteUrl() {
            // Given — use a separate WireMock server to simulate a different host
            var absoluteUrlServer = new WireMockServer(0);
            try {
                absoluteUrlServer.start();
                absoluteUrlServer.stubFor(get(urlPathEqualTo("/external/resources/greet"))
                        .withQueryParam("name", equalTo("Alice"))
                        .willReturn(ok("Hello from external!")));

                var registry = givenRegistryWithExtension(List.of(Map.of(
                        "uriTemplate",
                        "res://external/{name}",
                        "name",
                        "external",
                        "resolve",
                        Map.of("path", "http://localhost:" + absoluteUrlServer.port() + "/external/resources/greet"))));

                var resource = registry.getResources().getFirst();
                var request = new McpSchema.ReadResourceRequest("res://external/Alice", null);

                // When
                var result = resource.handler().apply(CONTEXT, request);

                // Then
                then(((McpSchema.TextResourceContents) result.contents().getFirst()).text())
                        .isEqualTo("Hello from external!");
            } finally {
                absoluteUrlServer.stop();
            }
        }

        @Test
        void shouldThrowResourceNotFoundWhenTemplateUriDoesNotMatch() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uriTemplate", "res://greet/{name}",
                    "name", "greet-template",
                    "resolve", Map.of("path", "/resources/greet"))));

            var resource = registry.getResources().getFirst();
            var request = new McpSchema.ReadResourceRequest("res://other/Alice", null);

            // When / Then
            thenThrownBy(() -> resource.handler().apply(CONTEXT, request))
                    .isInstanceOf(ResourceExecutionException.class)
                    .hasMessageContaining("res://other/Alice");
        }
    }

    @Nested
    class MixedModes {

        @Test
        void shouldRegisterBothInlineAndResolvedResources() {
            // Given
            var registry = givenRegistryWithExtension(List.of(
                    Map.of(
                            "uri", "res://static",
                            "name", "static-resource",
                            "inline", Map.of("text", "Static content")),
                    Map.of(
                            "uri", "res://dynamic",
                            "name", "dynamic-resource",
                            "resolve", Map.of("path", "/resources/dynamic"))));

            // When
            var resources = registry.getResources();

            // Then
            then(resources).hasSize(2);
            then(resources.stream()
                            .map(RegisteredResource::resource)
                            .map(McpSchema.Resource::name)
                            .toList())
                    .containsExactly("static-resource", "dynamic-resource");
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldThrowOnDuplicateResourceNames() {
            // Given
            var registry = givenRegistryWithExtension(List.of(
                    Map.of(
                            "uri", "res://a",
                            "name", "duplicate",
                            "inline", Map.of("text", "A")),
                    Map.of(
                            "uri", "res://b",
                            "name", "duplicate",
                            "inline", Map.of("text", "B"))));

            // When / Then
            thenThrownBy(registry::getResources)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate resource names");
        }

        @Test
        void shouldThrowOnDuplicateResourceUris() {
            // Given
            var registry = givenRegistryWithExtension(List.of(
                    Map.of(
                            "uri", "res://same",
                            "name", "first",
                            "inline", Map.of("text", "A")),
                    Map.of(
                            "uri", "res://same",
                            "name", "second",
                            "inline", Map.of("text", "B"))));

            // When / Then
            thenThrownBy(registry::getResources)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate resource URIs");
        }

        @Test
        void shouldThrowWhenBothUriAndUriTemplatePresent() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(List.of(Map.of(
                                    "uri", "res://a",
                                    "uriTemplate", "res://a/{x}",
                                    "name", "invalid",
                                    "inline", Map.of("text", "A"))))
                            .getResources())
                    .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                    ex instanceof IllegalArgumentException ? ex : ex.getCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("not both"));
        }

        @Test
        void shouldThrowWhenNeitherUriNorUriTemplatePresent() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(
                                    List.of(Map.of("name", "invalid", "inline", Map.of("text", "A"))))
                            .getResources())
                    .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                    ex instanceof IllegalArgumentException ? ex : ex.getCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("must define either"));
        }

        @Test
        void shouldThrowWhenBothInlineAndResolvePresent() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(List.of(Map.of(
                                    "uri",
                                    "res://a",
                                    "name",
                                    "invalid",
                                    "inline",
                                    Map.of("text", "A"),
                                    "resolve",
                                    Map.of("path", "/resources/a"))))
                            .getResources())
                    .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                    ex instanceof IllegalArgumentException ? ex : ex.getCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("not both"));
        }

        @Test
        void shouldThrowWhenNeitherInlineNorResolvePresent() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(List.of(Map.of("uri", "res://a", "name", "invalid")))
                            .getResources())
                    .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                    ex instanceof IllegalArgumentException ? ex : ex.getCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("must define either"));
        }
    }

    @Nested
    class Cache {

        @Test
        void shouldUpdateRegisteredResourcesCache() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "uri", "res://greet",
                    "name", "greet",
                    "inline", Map.of("text", "Hello"))));

            then(registry.getRegisteredResourcesCache()).isEmpty();

            // When
            registry.getResources();

            // Then
            then(registry.getRegisteredResourcesCache()).hasSize(1);
        }
    }

    private ResourceRegistry givenRegistryWithExtension(List<Map<String, Object>> resourcesExtension) {
        return givenRegistryWithExtension(resourcesExtension, noOpCredentialProvider);
    }

    private ResourceRegistry givenRegistryWithExtension(
            List<Map<String, Object>> resourcesExtension, CredentialProvider credentialProvider) {
        var openApi = new OpenAPI();
        if (resourcesExtension != null) {
            openApi.addExtension("x-mcp-resources", resourcesExtension);
        }
        when(openApiRegistry.openApi()).thenReturn(openApi);
        return new ResourceRegistry(
                openApiRegistry, restClient, OBJECT_MAPPER, credentialProvider, noOpEnricherChain, metricService);
    }
}
