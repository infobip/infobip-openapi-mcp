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

@ExtendWith(MockitoExtension.class)
class PromptRegistryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final McpRequestContext CONTEXT = new McpRequestContext();

    @Mock
    private OpenApiRegistry openApiRegistry;

    private WireMockServer wireMockServer;
    private RestClient restClient;
    private final CredentialProvider noOpCredentialProvider = context -> Optional.empty();

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
            var registry = new PromptRegistry(openApiRegistry, restClient, OBJECT_MAPPER, noOpCredentialProvider);

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
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("user_name");
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
                    "resolve", Map.of("path", "/prompts/simple", "method", "GET"))));

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
                    Map.of("path", "/prompts/greet", "method", "GET"))));

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
        void shouldResolvePromptViaPostWithJsonBody() {
            // Given
            wireMockServer.stubFor(post(urlPathEqualTo("/prompts/summarize"))
                    .withHeader("Content-Type", containing("application/json"))
                    .withRequestBody(matchingJsonPath("$.format", equalTo("markdown")))
                    .willReturn(okJson("""
                            {
                                "description": "Summarize",
                                "messages": [
                                    {"role": "user", "content": "Summarize in markdown."},
                                    {"role": "assistant", "content": "Here is the summary."}
                                ]
                            }
                            """)));

            var registry = givenRegistryWithExtension(List.of(Map.of(
                    "name",
                    "summarize",
                    "description",
                    "Summarize",
                    "arguments",
                    List.of(Map.of("name", "format", "description", "Format")),
                    "resolve",
                    Map.of("path", "/prompts/summarize", "method", "POST"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("summarize", Map.of("format", "markdown"));

            // When
            var result = prompt.handler().apply(CONTEXT, request);

            // Then
            then(result.messages()).hasSize(2);
            then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                    .isEqualTo("Summarize in markdown.");
            then(((McpSchema.TextContent) result.messages().get(1).content()).text())
                    .isEqualTo("Here is the summary.");
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
                            "resolve", Map.of("path", "/prompts/secure", "method", "GET"))),
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
                    "resolve", Map.of("path", "/prompts/failing", "method", "GET"))));

            var prompt = registry.getPrompts().getFirst();
            var request = new McpSchema.GetPromptRequest("failing", Map.of());

            // When / Then
            thenThrownBy(() -> prompt.handler().apply(CONTEXT, request)).isInstanceOf(RuntimeException.class);
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
                            "resolve", Map.of("path", "/prompts/greet", "method", "GET"))));

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
                    "resolve", Map.of("path", "/prompts/noargs", "method", "GET"))));

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
                                    Map.of("path", "/prompts/x", "method", "GET"),
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
        return new PromptRegistry(openApiRegistry, restClient, OBJECT_MAPPER, credentialProvider);
    }
}
