package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Registry for converting {@code x-mcp-resources} OpenAPI vendor extension definitions
 * into MCP resource specifications.
 *
 * <p>Supports two modes per resource:
 * <ul>
 *   <li><b>Inline mode</b> — resources with static {@code text} or {@code blob} content
 *       served verbatim.</li>
 *   <li><b>Resolved mode</b> — resources with a {@code resolve} block that delegates to a
 *       backend HTTP GET endpoint; the response body becomes the resource content verbatim.</li>
 * </ul>
 *
 * <p>A definition with a {@code uri} registers a concrete resource; a definition with a
 * {@code uriTemplate} registers an RFC 6570 resource template whose variables are extracted
 * from the requested URI and forwarded as query parameters in resolved mode.
 */
public class ResourceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceRegistry.class);
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{([^/}]+)}");

    private final OpenApiRegistry openApiRegistry;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CredentialProvider credentialProvider;
    private final ApiRequestEnricherChain enricherChain;
    private final MetricService metricService;

    private volatile List<RegisteredResource> registeredResourcesCache = List.of();

    public ResourceRegistry(
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
     * Reads the {@code x-mcp-resources} vendor extension from the OpenAPI spec and converts
     * each entry into a registered resource.
     *
     * @return a list of registered resources ready for MCP server registration
     */
    public List<RegisteredResource> getResources() {
        var definitions = parseExtension();
        if (definitions.isEmpty()) {
            LOGGER.debug("No resource definitions found in OpenAPI spec.");
            registeredResourcesCache = List.of();
            return registeredResourcesCache;
        }

        validateUniqueIdentifiers(definitions);

        registeredResourcesCache =
                definitions.stream().map(this::buildRegisteredResource).toList();
        LOGGER.info(
                "Registered {} resource(s): {}",
                registeredResourcesCache.size(),
                registeredResourcesCache.stream()
                        .map(r -> r.isTemplate()
                                ? r.template().uriTemplate()
                                : r.resource().uri())
                        .toList());
        return registeredResourcesCache;
    }

    /**
     * Returns the cached list of registered resources from the last {@link #getResources()} call.
     */
    public List<RegisteredResource> getRegisteredResourcesCache() {
        return registeredResourcesCache;
    }

    private List<ResourceExtensionDefinition> parseExtension() {
        var extensions = openApiRegistry.openApi().getExtensions();
        if (extensions == null) {
            LOGGER.debug("OpenAPI spec has no extensions, skipping resource parsing.");
            return List.of();
        }

        var resourcesExtension = extensions.get(Spec.MCP_RESOURCES_EXTENSION);
        if (resourcesExtension == null) {
            LOGGER.debug(
                    "OpenAPI spec has no '{}' extension. Available extensions: {}",
                    Spec.MCP_RESOURCES_EXTENSION,
                    extensions.keySet());
            return List.of();
        }

        LOGGER.debug("Found '{}' extension, parsing resource definitions.", Spec.MCP_RESOURCES_EXTENSION);
        return objectMapper.convertValue(resourcesExtension, new TypeReference<>() {});
    }

    private void validateUniqueIdentifiers(List<ResourceExtensionDefinition> definitions) {
        var duplicateNames = definitions.stream()
                .collect(Collectors.groupingBy(ResourceExtensionDefinition::name, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("Duplicate resource names in x-mcp-resources: " + duplicateNames);
        }

        var duplicateUris = definitions.stream()
                .map(d -> d.isTemplate() ? d.uriTemplate() : d.uri())
                .collect(Collectors.groupingBy(uri -> uri, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!duplicateUris.isEmpty()) {
            throw new IllegalArgumentException("Duplicate resource URIs in x-mcp-resources: " + duplicateUris);
        }
    }

    private RegisteredResource buildRegisteredResource(ResourceExtensionDefinition definition) {
        if (definition.isTemplate()) {
            return buildRegisteredResourceTemplate(definition);
        }
        return buildRegisteredConcreteResource(definition);
    }

    private RegisteredResource buildRegisteredConcreteResource(ResourceExtensionDefinition definition) {
        var resource = McpSchema.Resource.builder(definition.uri(), definition.name())
                .title(definition.title())
                .description(definition.description())
                .mimeType(definition.mimeType())
                .build();

        if (definition.inline() != null) {
            return new RegisteredResource(
                    resource, null, (context, request) -> readInline(definition, definition.inline(), request));
        }
        return new RegisteredResource(
                resource,
                null,
                (context, request) -> readResolved(definition, definition.resolve(), Map.of(), request, context));
    }

    private RegisteredResource buildRegisteredResourceTemplate(ResourceExtensionDefinition definition) {
        var template = McpSchema.ResourceTemplate.builder(definition.uriTemplate(), definition.name())
                .title(definition.title())
                .description(definition.description())
                .mimeType(definition.mimeType())
                .build();

        var variableNames = new java.util.ArrayList<String>();
        var matchPattern = compileTemplateMatcher(definition.uriTemplate(), variableNames);

        if (definition.inline() != null) {
            return new RegisteredResource(
                    null, template, (context, request) -> readInline(definition, definition.inline(), request));
        }
        return new RegisteredResource(null, template, (context, request) -> {
            var variables = extractTemplateVariables(definition, matchPattern, variableNames, request.uri());
            return readResolved(definition, definition.resolve(), variables, request, context);
        });
    }

    private Map<String, String> extractTemplateVariables(
            ResourceExtensionDefinition definition, Pattern matchPattern, List<String> variableNames, String uri) {
        Matcher matcher = matchPattern.matcher(uri);
        if (!matcher.matches()) {
            throw ResourceExecutionException.becauseNotFound(uri);
        }
        var variables = new LinkedHashMap<String, String>();
        for (int i = 0; i < variableNames.size(); i++) {
            variables.put(variableNames.get(i), matcher.group(i + 1));
        }
        return variables;
    }

    private Pattern compileTemplateMatcher(String uriTemplate, List<String> variableNames) {
        var regex = new StringBuilder();
        var matcher = TEMPLATE_VARIABLE_PATTERN.matcher(uriTemplate);
        int lastEnd = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(uriTemplate.substring(lastEnd, matcher.start())));
            variableNames.add(matcher.group(1));
            regex.append("([^/]+)");
            lastEnd = matcher.end();
        }
        regex.append(Pattern.quote(uriTemplate.substring(lastEnd)));
        return Pattern.compile(regex.toString());
    }

    private java.util.Set<String> extractPathVariableNames(String path) {
        var names = new java.util.HashSet<String>();
        var matcher = TEMPLATE_VARIABLE_PATTERN.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private McpSchema.ReadResourceResult readInline(
            ResourceExtensionDefinition definition,
            ResourceInlineContent content,
            McpSchema.ReadResourceRequest request) {
        metricService.recordResourceRead(request.uri());
        var timer = metricService.startResourceTimer();
        try {
            McpSchema.ResourceContents contents;
            if (content.text() != null) {
                contents =
                        new McpSchema.TextResourceContents(request.uri(), definition.mimeType(), content.text(), null);
            } else {
                contents =
                        new McpSchema.BlobResourceContents(request.uri(), definition.mimeType(), content.blob(), null);
            }
            timer.timeResourceRead(request.uri(), false);
            return new McpSchema.ReadResourceResult(List.of(contents), null);
        } catch (RuntimeException e) {
            timer.timeResourceRead(request.uri(), true);
            throw e;
        }
    }

    private McpSchema.ReadResourceResult readResolved(
            ResourceExtensionDefinition definition,
            ResourceResolveConfig resolveConfig,
            Map<String, String> templateVariables,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context) {
        metricService.recordResourceRead(request.uri());
        var timer = metricService.startResourceTimer();
        try {
            var result = resolveResource(definition, resolveConfig, templateVariables, request, context);
            timer.timeResourceRead(request.uri(), false);
            return result;
        } catch (RuntimeException e) {
            timer.timeResourceRead(request.uri(), true);
            throw e;
        }
    }

    private McpSchema.ReadResourceResult resolveResource(
            ResourceExtensionDefinition definition,
            ResourceResolveConfig resolveConfig,
            Map<String, String> templateVariables,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context) {
        LOGGER.debug("Resolving resource '{}' via GET {}", request.uri(), resolveConfig.path());

        var credential = credentialProvider.provide(context);

        var pathVariableNames = extractPathVariableNames(resolveConfig.path());
        var queryVariables = templateVariables.entrySet().stream()
                .filter(entry -> !pathVariableNames.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));

        var resolveCallTimer = metricService.startResourceTimer();
        try {
            var spec = restClient.get().uri(uriBuilder -> {
                if (resolveConfig.isAbsolute()) {
                    var builder = UriComponentsBuilder.fromUriString(resolveConfig.path());
                    queryVariables.forEach(builder::queryParam);
                    return builder.buildAndExpand(templateVariables).toUri();
                }
                var builder = uriBuilder.path(resolveConfig.path());
                queryVariables.forEach(builder::queryParam);
                return builder.build(templateVariables);
            });

            credential.ifPresent(auth -> spec.header(HttpHeaders.AUTHORIZATION, auth));

            var enrichedSpec = enricherChain.enrich(spec, context);

            var response = enrichedSpec.retrieve().toEntity(String.class);
            resolveCallTimer.timeResolveCall(
                    request.uri(), HttpStatus.valueOf(response.getStatusCode().value()));
            metricService.recordResourceResolveCall(
                    request.uri(), HttpStatus.valueOf(response.getStatusCode().value()));

            var mimeType = definition.mimeType() != null
                    ? definition.mimeType()
                    : response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            var contents = new McpSchema.TextResourceContents(request.uri(), mimeType, response.getBody(), null);
            return new McpSchema.ReadResourceResult(List.of(contents), null);
        } catch (HttpStatusCodeException e) {
            resolveCallTimer.timeResolveCall(
                    request.uri(), HttpStatus.valueOf(e.getStatusCode().value()));
            metricService.recordResourceResolveCall(
                    request.uri(), HttpStatus.valueOf(e.getStatusCode().value()));
            throw ResourceExecutionException.becauseBackendCallFailed(request.uri(), resolveConfig.path(), e);
        } catch (Exception e) {
            resolveCallTimer.timeResolveCall(request.uri(), HttpStatus.BAD_GATEWAY);
            metricService.recordResourceResolveCall(request.uri(), HttpStatus.BAD_GATEWAY);
            throw ResourceExecutionException.becauseBackendCallFailed(request.uri(), resolveConfig.path(), e);
        }
    }
}
