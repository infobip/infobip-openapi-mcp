package com.infobip.openapi.mcp.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Registry for converting {@code x-mcp-prompts} OpenAPI vendor extension definitions
 * into MCP prompt specifications.
 *
 * <p>Supports two modes per prompt:
 * <ul>
 *   <li><b>Inline mode</b> — prompts with a {@code messages} list containing
 *       Mustache templates that are rendered server-side from the user-supplied arguments.</li>
 *   <li><b>Resolved mode</b> — prompts with a {@code resolve} block that
 *       delegates to a backend HTTP endpoint.</li>
 * </ul>
 */
public class PromptRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptRegistry.class);

    private final OpenApiRegistry openApiRegistry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CredentialProvider credentialProvider;
    private final ApiRequestEnricherChain enricherChain;
    private final MetricService metricService;
    private final MustacheTemplateRenderer templateRenderer = new MustacheTemplateRenderer();

    private volatile List<RegisteredPrompt> registeredPromptsCache = List.of();

    public PromptRegistry(
            OpenApiRegistry openApiRegistry,
            RestClient restClient,
            ObjectMapper objectMapper,
            CredentialProvider credentialProvider,
            ApiRequestEnricherChain enricherChain,
            MetricService metricService) {
        this.openApiRegistry = openApiRegistry;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.credentialProvider = credentialProvider;
        this.enricherChain = enricherChain;
        this.metricService = metricService;
    }

    /**
     * Reads the {@code x-mcp-prompts} vendor extension from the OpenAPI spec and converts
     * each entry into a registered prompt.
     *
     * @return a list of registered prompts ready for MCP server registration
     */
    public List<RegisteredPrompt> getPrompts() {
        var definitions = parseExtension();
        if (definitions.isEmpty()) {
            LOGGER.debug("No prompt definitions found in OpenAPI spec.");
            registeredPromptsCache = List.of();
            return registeredPromptsCache;
        }

        validateUniqueNames(definitions);

        templateRenderer.clear();

        registeredPromptsCache =
                definitions.stream().map(this::buildRegisteredPrompt).toList();
        LOGGER.info(
                "Registered {} prompt(s): {}",
                registeredPromptsCache.size(),
                registeredPromptsCache.stream().map(p -> p.prompt().name()).toList());
        return registeredPromptsCache;
    }

    /**
     * Returns the cached list of registered prompts from the last {@link #getPrompts()} call.
     */
    public List<RegisteredPrompt> getRegisteredPromptsCache() {
        return registeredPromptsCache;
    }

    private List<PromptExtensionDefinition> parseExtension() {
        var extensions = openApiRegistry.openApi().getExtensions();
        if (extensions == null) {
            LOGGER.debug("OpenAPI spec has no extensions, skipping prompt parsing.");
            return List.of();
        }

        var promptsExtension = extensions.get(Spec.MCP_PROMPTS_EXTENSION);
        if (promptsExtension == null) {
            LOGGER.debug(
                    "OpenAPI spec has no '{}' extension. Available extensions: {}",
                    Spec.MCP_PROMPTS_EXTENSION,
                    extensions.keySet());
            return List.of();
        }

        LOGGER.debug("Found '{}' extension, parsing prompt definitions.", Spec.MCP_PROMPTS_EXTENSION);
        return objectMapper.convertValue(promptsExtension, new TypeReference<>() {});
    }

    private void validateUniqueNames(List<PromptExtensionDefinition> definitions) {
        var duplicates = definitions.stream()
                .collect(Collectors.groupingBy(PromptExtensionDefinition::name, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate prompt names in x-mcp-prompts: " + duplicates);
        }
    }

    private RegisteredPrompt buildRegisteredPrompt(PromptExtensionDefinition definition) {
        var arguments = definition.arguments().stream()
                .map(arg -> new McpSchema.PromptArgument(arg.name(), arg.description(), arg.required()))
                .toList();

        var prompt = new McpSchema.Prompt(definition.name(), definition.description(), arguments);

        if (definition.messages() != null) {
            return buildInlinePrompt(prompt, definition);
        }
        return buildResolvedPrompt(prompt, definition);
    }

    private RegisteredPrompt buildInlinePrompt(McpSchema.Prompt prompt, PromptExtensionDefinition definition) {
        var messages = definition.messages();
        var requiredArgNames = definition.arguments().stream()
                .filter(PromptExtensionArgument::required)
                .map(PromptExtensionArgument::name)
                .toList();

        templateRenderer.compileTemplates(definition.name(), messages);

        return new RegisteredPrompt(prompt, (context, request) -> {
            metricService.recordPromptCall(definition.name());
            var promptCallTimer = metricService.startPromptTimer();

            try {
                var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();

                var missingRequired = requiredArgNames.stream()
                        .filter(name -> !args.containsKey(name) || args.get(name) == null)
                        .toList();
                if (!missingRequired.isEmpty()) {
                    throw PromptExecutionException.becauseMissingRequiredArguments(definition.name(), missingRequired);
                }

                var promptMessages = IntStream.range(0, messages.size())
                        .mapToObj(i -> {
                            var rendered = templateRenderer.render(definition.name(), i, args);
                            return new McpSchema.PromptMessage(
                                    messages.get(i).role(), new McpSchema.TextContent(rendered));
                        })
                        .toList();

                promptCallTimer.timePromptCall(definition.name(), false);
                return new McpSchema.GetPromptResult(definition.description(), promptMessages);
            } catch (RuntimeException e) {
                promptCallTimer.timePromptCall(definition.name(), true);
                throw e;
            }
        });
    }

    private RegisteredPrompt buildResolvedPrompt(McpSchema.Prompt prompt, PromptExtensionDefinition definition) {
        return new RegisteredPrompt(prompt, (context, request) -> {
            metricService.recordPromptCall(definition.name());
            var promptCallTimer = metricService.startPromptTimer();

            try {
                var result = resolvePrompt(definition.name(), definition.resolve(), request, context);
                promptCallTimer.timePromptCall(definition.name(), false);
                return result;
            } catch (RuntimeException e) {
                promptCallTimer.timePromptCall(definition.name(), true);
                throw e;
            }
        });
    }

    private McpSchema.GetPromptResult resolvePrompt(
            String promptName,
            PromptResolveConfig resolveConfig,
            McpSchema.GetPromptRequest request,
            McpRequestContext context) {
        var args = request.arguments() != null ? request.arguments() : Map.<String, Object>of();

        LOGGER.debug("Resolving prompt '{}' via GET {}", promptName, resolveConfig.path());

        var credential = credentialProvider.provide(context);

        var spec = restClient.get().uri(uriBuilder -> {
            if (resolveConfig.isAbsolute()) {
                var builder = UriComponentsBuilder.fromUriString(resolveConfig.path());
                args.forEach((key, value) -> builder.queryParam(key, value.toString()));
                return builder.build().toUri();
            }
            var builder = uriBuilder.path(resolveConfig.path());
            args.forEach((key, value) -> builder.queryParam(key, value.toString()));
            return builder.build();
        });

        credential.ifPresent(auth -> spec.header(HttpHeaders.AUTHORIZATION, auth));

        var enrichedSpec = enricherChain.enrich(spec, context);

        var resolveCallTimer = metricService.startPromptTimer();
        String responseBody;
        try {
            responseBody = enrichedSpec.retrieve().body(String.class);
            resolveCallTimer.timeResolveCall(promptName, HttpStatus.OK);
            metricService.recordPromptResolveCall(promptName, HttpStatus.OK);
        } catch (HttpStatusCodeException e) {
            resolveCallTimer.timeResolveCall(promptName, e.getStatusCode());
            metricService.recordPromptResolveCall(promptName, e.getStatusCode());
            throw PromptExecutionException.becauseBackendCallFailed(promptName, resolveConfig.path(), e);
        } catch (Exception e) {
            resolveCallTimer.timeResolveCall(promptName, HttpStatus.BAD_GATEWAY);
            metricService.recordPromptResolveCall(promptName, HttpStatus.BAD_GATEWAY);
            throw PromptExecutionException.becauseBackendCallFailed(promptName, resolveConfig.path(), e);
        }

        return toGetPromptResult(promptName, responseBody);
    }

    private McpSchema.GetPromptResult toGetPromptResult(String promptName, String responseBody) {
        try {
            var response = objectMapper.readValue(responseBody, PromptResolveResponse.class);
            var messages = response.messages().stream()
                    .map(msg -> new McpSchema.PromptMessage(msg.role(), new McpSchema.TextContent(msg.content())))
                    .toList();
            return new McpSchema.GetPromptResult(response.description(), messages);
        } catch (Exception e) {
            throw PromptExecutionException.becauseBackendResponseInvalid(promptName, e);
        }
    }
}
