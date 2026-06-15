package com.infobip.openapi.mcp.prompt;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.HashMap;
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

@ExtendWith(MockitoExtension.class)
class PromptRegistryTest {

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
            var prompts = registry.getPrompts();

            // Then
            then(prompts).isEmpty();
        }

        @Test
        void shouldReturnEmptyListWhenNoPromptsExtension() {
            // Given
            var openApi = new OpenAPI();
            openApi.addExtension("x-other", "value");
            when(openApiRegistry.openApi()).thenReturn(openApi);
            var registry = new PromptRegistry(
                    openApiRegistry,
                    restClient,
                    OBJECT_MAPPER,
                    noOpCredentialProvider,
                    noOpEnricherChain,
                    metricService);

            // When
            var prompts = registry.getPrompts();

            // Then
            then(prompts).isEmpty();
        }
    }

    @Nested
    class InlineTemplates {

        @Test
        void shouldRegisterInlinePromptWithArguments() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet a user",
                    "arguments",
                    List.of(Map.of("name", "user_name", "description", "User name", "required", true)),
                    "messages",
                    List.of(Map.of("role", "user", "content", "Hello {{user_name}}, welcome!")))));

            // When
            var prompts = registry.getPrompts();

            // Then
            then(prompts).hasSize(1);
            var prompt = prompts.getFirst().prompt();
            then(prompt.name()).isEqualTo("greet");
            then(prompt.description()).isEqualTo("Greet a user");
            then(prompt.arguments()).hasSize(1);
            then(prompt.arguments().getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.PromptArgument("user_name", "User name", true));
        }

        @Test
        void shouldRenderInlineTemplateWithArguments() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet",
                    "arguments",
                    List.of(Map.of("name", "user_name", "description", "Name", "required", true)),
                    "messages",
                    List.of(Map.of("role", "user", "content", "Hello {{user_name}}!")))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("greet", Map.of("user_name", "Alice"));

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(result.description()).isEqualTo("Greet");
            then(result.messages()).hasSize(1);
            then(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Hello Alice!");
        }

        @Test
        void shouldRenderMultipleMessagesInOrder() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "fewshot",
                    "description", "Few-shot",
                    "messages",
                            List.of(
                                    Map.of("role", "user", "content", "Summarize {{topic}} briefly."),
                                    Map.of("role", "assistant", "content", "Here is a brief summary of {{topic}}."),
                                    Map.of("role", "user", "content", "Now expand on {{topic}}.")))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("fewshot", Map.of("topic", "Java"));

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(result.messages()).hasSize(3);
            then(result.messages().stream().map(McpSchema.PromptMessage::role).toList())
                    .containsExactly(McpSchema.Role.USER, McpSchema.Role.ASSISTANT, McpSchema.Role.USER);
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Summarize Java briefly.");
            then(((McpSchema.TextContent) result.messages().get(2).content()).text())
                    .isEqualTo("Now expand on Java.");
        }

        @Test
        void shouldThrowWhenRequiredArgumentIsMissing() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet",
                    "arguments",
                    List.of(Map.of("name", "user_name", "description", "Name", "required", true)),
                    "messages",
                    List.of(Map.of("role", "user", "content", "Hello {{user_name}}!")))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("greet", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("user_name");
        }

        @Test
        void shouldThrowWhenRequiredArgumentHasNullValue() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet",
                    "arguments",
                    List.of(Map.of("name", "user_name", "description", "Name", "required", true)),
                    "messages",
                    List.of(Map.of("role", "user", "content", "Hello {{user_name}}!")))));

            var prompt = registry.getPrompts().getFirst();
            var args = new HashMap<String, Object>();
            args.put("user_name", null);
            var request = new McpSchema.GetPromptRequest("greet", args);

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("user_name");
        }

        @Test
        void shouldThrowWithPromptExecutionExceptionForMissingRequiredArgument() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet",
                    "arguments",
                    List.of(Map.of("name", "user_name", "description", "Name", "required", true)),
                    "messages",
                    List.of(Map.of("role", "user", "content", "Hello {{user_name}}!")))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("greet", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request)).isInstanceOf(PromptExecutionException.class);
        }

        @Test
        void shouldRenderMissingOptionalArgumentAsEmpty() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet",
                    "arguments",
                    List.of(Map.of("name", "greeting", "description", "Greeting word")),
                    "messages",
                    List.of(Map.of("role", "user", "content", "{{greeting}} World!")))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("greet", Map.of());

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo(" World!");
        }

        @Test
        void shouldNotHtmlEscapeArgumentValues() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "code",
                    "description", "Code",
                    "messages", List.of(Map.of("role", "user", "content", "Code: {{snippet}}")))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("code", Map.of("snippet", "<div class=\"test\"> & foo</div>"));

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Code: <div class=\"test\"> & foo</div>");
        }
    }

    @Nested
    class ResolvedBackendResolution {

        @Test
        void shouldRegisterResolvedPromptWithNoArguments() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "simple-prompt",
                    "description", "A simple prompt",
                    "resolve", Map.of("path", "/prompts/simple"))));

            // When
            var prompts = registry.getPrompts();

            // Then
            then(prompts).hasSize(1);
            var prompt = prompts.getFirst().prompt();
            then(prompt.name()).isEqualTo("simple-prompt");
            then(prompt.description()).isEqualTo("A simple prompt");
            then(prompt.arguments()).isEmpty();
        }

        @Test
        void shouldResolvePromptViaGetWithQueryParameters() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/greet"))
                    .withQueryParam("name", equalTo("Alice"))
                    .willReturn(okJson("""
                            {
                                "description": "Greet a user",
                                "messages": [
                                    {"role": "user", "content": "Hello Alice!"}
                                ]
                            }
                            """)));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "greet",
                    "description",
                    "Greet",
                    "arguments",
                    List.of(Map.of("name", "name", "description", "Name", "required", true)),
                    "resolve",
                    Map.of("path", "/prompts/greet"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("greet", Map.of("name", "Alice"));

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(result.description()).isEqualTo("Greet a user");
            then(result.messages()).hasSize(1);
            then(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Hello Alice!");
        }

        @Test
        void shouldForwardCredentialsInAuthorizationHeader() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/secure"))
                    .withHeader("Authorization", equalTo("Bearer test-token"))
                    .willReturn(okJson("""
                            {
                                "description": "Secure prompt",
                                "messages": [{"role": "user", "content": "Secret data."}]
                            }
                            """)));

            CredentialProvider credentialProvider = context -> Optional.of("Bearer test-token");
            var registry = givenRegistryWithExtension(
                    List.of(Map.of(
                            "name", "secure",
                            "description", "Secure",
                            "resolve", Map.of("path", "/prompts/secure"))),
                    credentialProvider);

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("secure", Map.of());

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Secret data.");
            wireMockServer.verify(getRequestedFor(urlPathEqualTo("/prompts/secure"))
                    .withHeader("Authorization", equalTo("Bearer test-token")));
        }

        @Test
        void shouldThrowWhenBackendReturnsError() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/failing"))
                    .willReturn(serverError().withBody("Internal error")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "failing",
                    "description", "Failing",
                    "resolve", Map.of("path", "/prompts/failing"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("failing", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("failing")
                    .hasMessageContaining("/prompts/failing");
        }

        @Test
        void shouldThrowWhenBackendReturnsMalformedJson() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/malformed"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{invalid json}")));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "malformed",
                    "description", "Malformed",
                    "resolve", Map.of("path", "/prompts/malformed"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("malformed", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        void shouldThrowWhenBackendReturnsEmptyMessages() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/empty")).willReturn(okJson("""
                            {
                                "description": "Empty prompt",
                                "messages": []
                            }
                            """)));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "empty-msgs",
                    "description", "Empty",
                    "resolve", Map.of("path", "/prompts/empty"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("empty-msgs", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("empty-msgs");
        }

        @Test
        void shouldResolvePromptViaAbsoluteUrl() {
            // Given — use a separate WireMock server to simulate a different host
            var absoluteUrlServer = new WireMockServer(0);
            try {
                absoluteUrlServer.start();
                absoluteUrlServer.stubFor(get(urlPathEqualTo("/external/prompts/greet"))
                        .withQueryParam("name", equalTo("Alice"))
                        .willReturn(okJson("""
                                {
                                    "description": "External greeting",
                                    "messages": [{"role": "user", "content": "Hello from external!"}]
                                }
                                """)));

                var registry = givenRegistryWithExtension(List.of(Map.of(
                        "name",
                        "external",
                        "description",
                        "External",
                        "arguments",
                        List.of(Map.of("name", "name", "description", "Name")),
                        "resolve",
                        Map.of("path", "http://localhost:" + absoluteUrlServer.port() + "/external/prompts/greet"))));

                var prompt = registry.getPrompts().getFirst();
                var request = new McpSchema.GetPromptRequest("external", Map.of("name", "Alice"));

                // When
                var result = prompt.handler().apply(CONTEXT, request);

                // Then
                then(result.description()).isEqualTo("External greeting");
                then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                        .isEqualTo("Hello from external!");
            } finally {
                absoluteUrlServer.stop();
            }
        }

        @Test
        void shouldThrowWhenBackendReturnsMessageWithBlankContent() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/blank")).willReturn(okJson("""
                            {
                                "description": "Blank content",
                                "messages": [{"role": "user", "content": ""}]
                            }
                            """)));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "blank-msg",
                    "description", "Blank",
                    "resolve", Map.of("path", "/prompts/blank"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("blank-msg", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("blank-msg");
        }

        @Test
        void shouldThrowWhenBackendReturnsMessageWithNullRole() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/nullrole")).willReturn(okJson("""
                            {
                                "description": "Null role",
                                "messages": [{"content": "hello"}]
                            }
                            """)));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "null-role",
                    "description", "Null role",
                    "resolve", Map.of("path", "/prompts/nullrole"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("null-role", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request))
                    .isInstanceOf(PromptExecutionException.class)
                    .hasMessageContaining("null-role");
        }

        @Test
        void shouldThrowWhenBackendIsUnreachable() {
            // Given — use a port with no server to simulate connection error
            var unreachableRestClient = RestClient.builder()
                    .baseUrl("http://localhost:" + 19999)
                    .requestFactory(new SimpleClientHttpRequestFactory())
                    .build();
            var openApi = new OpenAPI();
            openApi.addExtension(
                    "x-mcp-prompts",
                    List.of(Map.of(
                            "name", "unreachable",
                            "description", "Unreachable",
                            "resolve", Map.of("path", "/prompts/unreachable"))));
            when(openApiRegistry.openApi()).thenReturn(openApi);
            var registry = new PromptRegistry(
                    openApiRegistry,
                    unreachableRestClient,
                    OBJECT_MAPPER,
                    noOpCredentialProvider,
                    noOpEnricherChain,
                    metricService);

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("unreachable", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request)).isInstanceOf(PromptExecutionException.class);
        }
    }

    @Nested
    class MixedModes {

        @Test
        void shouldRegisterBothInlineAndResolvedPrompts() {
            // Given
            var registry = givenRegistryWithExtension(List.of(
                    Map.of(
                            "name", "static-greet",
                            "description", "Static greeting",
                            "messages", List.of(Map.of("role", "user", "content", "Hello {{name}}!"))),
                    Map.of(
                            "name", "dynamic-greet",
                            "description", "Dynamic greeting",
                            "resolve", Map.of("path", "/prompts/greet"))));

            // When
            var prompts = registry.getPrompts();

            // Then
            then(prompts).hasSize(2);
            then(prompts.stream().map(rp -> rp.prompt().name()).toList())
                    .containsExactly("static-greet", "dynamic-greet");
        }

        @Test
        void shouldHandleNullArguments() {
            // Given
            wireMockServer.stubFor(get(urlPathEqualTo("/prompts/noargs")).willReturn(okJson("""
                            {
                                "description": "No args",
                                "messages": [{"role": "user", "content": "Static prompt."}]
                            }
                            """)));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "noargs",
                    "description", "No args",
                    "resolve", Map.of("path", "/prompts/noargs"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("noargs", null);

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Static prompt.");
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldThrowOnDuplicatePromptNames() {
            // Given
            var registry = givenRegistryWithExtension(List.of(
                    Map.of(
                            "name", "greet",
                            "description", "First",
                            "messages", List.of(Map.of("role", "user", "content", "Hello"))),
                    Map.of(
                            "name", "greet",
                            "description", "Second",
                            "messages", List.of(Map.of("role", "user", "content", "Hi")))));

            // When / Then
            thenThrownBy(registry::getPrompts)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate prompt names");
        }

        @Test
        void shouldThrowWhenBothResolveAndMessagesPresent() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(List.of(Map.of(
                                    "name",
                                    "invalid",
                                    "description",
                                    "Invalid",
                                    "resolve",
                                    Map.of("path", "/prompts/x"),
                                    "messages",
                                    List.of(Map.of("role", "user", "content", "Hello")))))
                            .getPrompts())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not both");
        }

        @Test
        void shouldThrowWhenNeitherResolveNorMessagesPresent() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(List.of(Map.of("name", "invalid", "description", "Invalid")))
                            .getPrompts())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must define either");
        }

        @Test
        void shouldThrowWhenMessagesListIsEmpty() {
            // Given / When / Then
            thenThrownBy(() -> givenRegistryWithExtension(List.of(Map.of(
                                    "name", "invalid",
                                    "description", "Invalid",
                                    "messages", List.of())))
                            .getPrompts())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }
    }

    @Nested
    class Cache {

        @Test
        void shouldUpdateRegisteredPromptsCache() {
            // Given
            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name", "greet",
                    "description", "Greet",
                    "messages", List.of(Map.of("role", "user", "content", "Hello")))));

            then(registry.getRegisteredPromptsCache()).isEmpty();

            // When
            registry.getPrompts();

            // Then
            then(registry.getRegisteredPromptsCache()).hasSize(1);
        }
    }

    private PromptRegistry givenRegistryWithExtension(List<Map<String, Object>> promptsExtension) {
        return givenRegistryWithExtension(promptsExtension, noOpCredentialProvider);
    }

    private PromptRegistry givenRegistryWithExtension(
            List<Map<String, Object>> promptsExtension, CredentialProvider credentialProvider) {
        var openApi = new OpenAPI();
        if (promptsExtension != null) {
            openApi.addExtension("x-mcp-prompts", promptsExtension);
        }
        when(openApiRegistry.openApi()).thenReturn(openApi);
        return new PromptRegistry(
                openApiRegistry, restClient, OBJECT_MAPPER, credentialProvider, noOpEnricherChain, metricService);
    }
}
