package com.infobip.openapi.mcp.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Registry for converting {@code x-mcp-prompts} OpenAPI vendor extension definitions
 * into MCP prompt specifications.
 * <p>
 * Each prompt definition is converted into a {@link RegisteredPrompt} containing the MCP
 * prompt schema and a handler that resolves the prompt by calling a backend endpoint.
 */
public class PromptRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptRegistry.class);

    private final OpenApiRegistry openApiRegistry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CredentialProvider credentialProvider;

    public PromptRegistry(
            OpenApiRegistry openApiRegistry,
            RestClient restClient,
            ObjectMapper objectMapper,
            CredentialProvider credentialProvider) {
        this.openApiRegistry = openApiRegistry;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.credentialProvider = credentialProvider;
    }

    /**
     * Reads the {@code x-mcp-prompts} vendor extension from the OpenAPI spec and converts
     * each entry into a registered prompt.
     *
     * @return a list of registered prompts ready for MCP server registration
     */
    @SuppressWarnings("unchecked")
    public List<RegisteredPrompt> getPrompts() {
        var extensions = openApiRegistry.openApi().getExtensions();
        if (extensions == null) {
            return List.of();
        }

        var promptsExtension = extensions.get(Spec.MCP_PROMPTS_EXTENSION);
        if (!(promptsExtension instanceof Map<?, ?> promptsMap)) {
            return List.of();
        }

        return ((Map<String, Object>) promptsMap)
                .entrySet().stream()
                        .map(entry -> buildRegisteredPrompt(
                                entry.getKey(),
                                objectMapper.convertValue(entry.getValue(), PromptExtensionDefinition.class)))
                        .toList();
    }

    private RegisteredPrompt buildRegisteredPrompt(String name, PromptExtensionDefinition definition) {
        var arguments = definition.arguments().entrySet().stream()
                .map(entry -> new McpSchema.PromptArgument(
                        entry.getKey(),
                        entry.getValue().description(),
                        entry.getValue().required()))
                .toList();

        var prompt = new McpSchema.Prompt(name, definition.description(), arguments);

        return new RegisteredPrompt(
                prompt, (context, request) -> resolvePrompt(name, definition.resolve(), request, context));
    }

    private McpSchema.GetPromptResult resolvePrompt(
            String promptName,
            PromptResolveConfig resolveConfig,
            McpSchema.GetPromptRequest request,
            McpRequestContext context) {
        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();
        var method = HttpMethod.valueOf(resolveConfig.method().toUpperCase(Locale.ROOT));

        LOGGER.debug("Resolving prompt '{}' via {} {}", promptName, method, resolveConfig.path());

        var credential = credentialProvider.provide(context);

        var spec = restClient.method(method).uri(uriBuilder -> {
            var builder = uriBuilder.path(resolveConfig.path());
            if (method == HttpMethod.GET) {
                args.forEach((key, value) -> builder.queryParam(key, value.toString()));
            }
            return builder.build();
        });

        if (method == HttpMethod.POST) {
            spec.contentType(MediaType.APPLICATION_JSON).body(args);
        }

        credential.ifPresent(auth -> spec.header(HttpHeaders.AUTHORIZATION, auth));

        var responseBody = spec.retrieve().body(String.class);

        return toGetPromptResult(promptName, responseBody);
    }

    private McpSchema.GetPromptResult toGetPromptResult(String promptName, String responseBody) {
        try {
            var response = objectMapper.readValue(responseBody, PromptResolveResponse.class);
            var messages = response.messages().stream()
                    .map(msg -> new McpSchema.PromptMessage(
                            toMcpRole(msg.role()), new McpSchema.TextContent(msg.content())))
                    .toList();
            return new McpSchema.GetPromptResult(response.description(), messages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse prompt resolve response for '" + promptName + "'", e);
        }
    }

    private McpSchema.Role toMcpRole(String role) {
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "user" -> McpSchema.Role.USER;
            case "assistant" -> McpSchema.Role.ASSISTANT;
            default -> throw new IllegalArgumentException("Unknown prompt message role: " + role);
        };
    }
}
