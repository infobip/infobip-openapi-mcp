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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @Test
    void shouldRegisterPromptWithNoArguments() {
        // Given
        var registry = givenRegistryWithExtension(Map.of(
                "simple-prompt",
                Map.of(
                        "description",
                        "A simple prompt",
                        "resolve",
                        Map.of("path", "/prompts/simple", "method", "GET"))));

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
    void shouldRegisterPromptWithArguments() {
        // Given
        var registry = givenRegistryWithExtension(Map.of(
                "greet",
                Map.of(
                        "description",
                        "Greet a user",
                        "arguments",
                        Map.of(
                                "name",
                                Map.of("description", "User name", "required", true),
                                "format",
                                Map.of("description", "Output format")),
                        "resolve",
                        Map.of("path", "/prompts/greet", "method", "POST"))));

        // When
        var prompts = registry.getPrompts();

        // Then
        then(prompts).hasSize(1);
        var args = prompts.getFirst().prompt().arguments();
        then(args).hasSize(2);

        var nameArg =
                args.stream().filter(a -> a.name().equals("name")).findFirst().orElseThrow();
        then(nameArg).usingRecursiveComparison().isEqualTo(new McpSchema.PromptArgument("name", "User name", true));

        var formatArg =
                args.stream().filter(a -> a.name().equals("format")).findFirst().orElseThrow();
        then(formatArg)
                .usingRecursiveComparison()
                .isEqualTo(new McpSchema.PromptArgument("format", "Output format", false));
    }

    @Test
    void shouldRegisterMultiplePrompts() {
        // Given
        var promptsMap = new LinkedHashMap<String, Object>();
        promptsMap.put(
                "first",
                Map.of("description", "First prompt", "resolve", Map.of("path", "/prompts/first", "method", "GET")));
        promptsMap.put(
                "second",
                Map.of("description", "Second prompt", "resolve", Map.of("path", "/prompts/second", "method", "POST")));
        var registry = givenRegistryWithExtension(promptsMap);

        // When
        var prompts = registry.getPrompts();

        // Then
        then(prompts).hasSize(2);
        then(prompts.stream().map(rp -> rp.prompt().name()).toList()).containsExactly("first", "second");
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

        var registry = givenRegistryWithExtension(Map.of(
                "greet",
                Map.of(
                        "description", "Greet",
                        "arguments", Map.of("name", Map.of("description", "Name", "required", true)),
                        "resolve", Map.of("path", "/prompts/greet", "method", "GET"))));

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

        var registry = givenRegistryWithExtension(Map.of(
                "summarize",
                Map.of(
                        "description", "Summarize",
                        "arguments", Map.of("format", Map.of("description", "Format")),
                        "resolve", Map.of("path", "/prompts/summarize", "method", "POST"))));

        var prompt = registry.getPrompts().getFirst();
        var request = new McpSchema.GetPromptRequest("summarize", Map.of("format", "markdown"));

        // When
        var result = prompt.handler().apply(CONTEXT, request);

        // Then
        then(result.messages()).hasSize(2);
        then(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
        then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                .isEqualTo("Summarize in markdown.");
        then(result.messages().get(1).role()).isEqualTo(McpSchema.Role.ASSISTANT);
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
                Map.of(
                        "secure",
                        Map.of("description", "Secure", "resolve", Map.of("path", "/prompts/secure", "method", "GET"))),
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
    void shouldResolvePromptWithBothRoles() {
        // Given
        wireMockServer.stubFor(get(urlPathEqualTo("/prompts/fewshot")).willReturn(okJson("""
                        {
                            "description": "Few-shot prompt",
                            "messages": [
                                {"role": "user", "content": "User message"},
                                {"role": "assistant", "content": "Assistant message"},
                                {"role": "user", "content": "Follow-up"}
                            ]
                        }
                        """)));

        var registry = givenRegistryWithExtension(Map.of(
                "fewshot",
                Map.of("description", "Few-shot", "resolve", Map.of("path", "/prompts/fewshot", "method", "GET"))));

        var prompt = registry.getPrompts().getFirst();
        var request = new McpSchema.GetPromptRequest("fewshot", Map.of());

        // When
        var result = prompt.handler().apply(CONTEXT, request);

        // Then
        then(result.messages()).hasSize(3);
        then(result.messages().stream().map(McpSchema.PromptMessage::role).toList())
                .containsExactly(McpSchema.Role.USER, McpSchema.Role.ASSISTANT, McpSchema.Role.USER);
    }

    @Test
    void shouldThrowWhenBackendReturnsError() {
        // Given
        wireMockServer.stubFor(
                get(urlPathEqualTo("/prompts/failing")).willReturn(serverError().withBody("Internal error")));

        var registry = givenRegistryWithExtension(Map.of(
                "failing",
                Map.of("description", "Failing", "resolve", Map.of("path", "/prompts/failing", "method", "GET"))));

        var prompt = registry.getPrompts().getFirst();
        var request = new McpSchema.GetPromptRequest("failing", Map.of());

        // When / Then
        thenThrownBy(() -> prompt.handler().apply(CONTEXT, request)).isInstanceOf(RuntimeException.class);
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

        var registry = givenRegistryWithExtension(Map.of(
                "noargs",
                Map.of("description", "No args", "resolve", Map.of("path", "/prompts/noargs", "method", "GET"))));

        var prompt = registry.getPrompts().getFirst();
        var request = new McpSchema.GetPromptRequest("noargs", null);

        // When
        var result = prompt.handler().apply(CONTEXT, request);

        // Then
        then(((McpSchema.TextContent) result.messages().getFirst().content()).text())
                .isEqualTo("Static prompt.");
    }

    private PromptRegistry givenRegistryWithExtension(Map<String, Object> promptsExtension) {
        return givenRegistryWithExtension(promptsExtension, noOpCredentialProvider);
    }

    private PromptRegistry givenRegistryWithExtension(
            Map<String, Object> promptsExtension, CredentialProvider credentialProvider) {
        var openApi = new OpenAPI();
        if (promptsExtension != null) {
            openApi.addExtension("x-mcp-prompts", promptsExtension);
        }
        when(openApiRegistry.openApi()).thenReturn(openApi);
        return new PromptRegistry(openApiRegistry, restClient, OBJECT_MAPPER, credentialProvider);
    }
}
