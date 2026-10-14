package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.apps.AppMeta;
import com.infobip.openapi.mcp.apps.AppResourceExtension;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategy;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.PathItem;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registry for converting OpenAPI operations marked with the {@code x-mcp-resource} vendor extension into MCP
 * resource specifications.
 * <p>
 * Each marked operation becomes either a static MCP {@code Resource} (no path or query parameters) or a
 * {@code ResourceTemplate} (path and/or query parameters present). The {@code uri}/{@code uriTemplate},
 * {@code name}, {@code title}, {@code description} and {@code mimeType} are all derived from the OpenAPI
 * operation.
 * <p>
 * Operations marked with the MCP Apps {@code x-mcp-app-resource} vendor extension are registered as static
 * {@code ui://} resources with the MCP Apps MIME type and {@code _meta.ui} metadata. They never become resource
 * templates, and their configuration is validated at registration time.
 */
public class ResourceRegistry {

    private static final String DEFAULT_MIME_TYPE = "application/json";
    private static final String PREFERRED_SUCCESS_RESPONSE_CODE = "200";
    private static final String SUCCESS_RESPONSE_RANGE = "2XX";

    private final OpenApiRegistry openApiRegistry;
    private final NamingStrategy namingStrategy;
    private final ResourceUriBuilder resourceUriBuilder;
    private final ResourceHandler resourceHandler;
    private final ObjectMapper objectMapper;

    private List<RegisteredResource> registeredResourcesCache = List.of();
    private Set<String> appResourceUrisCache = Set.of();

    public ResourceRegistry(
            OpenApiRegistry openApiRegistry,
            NamingStrategy namingStrategy,
            ResourceUriBuilder resourceUriBuilder,
            ResourceHandler resourceHandler) {
        this(
                openApiRegistry,
                namingStrategy,
                resourceUriBuilder,
                resourceHandler,
                JsonMapper.builder().build());
    }

    public ResourceRegistry(
            OpenApiRegistry openApiRegistry,
            NamingStrategy namingStrategy,
            ResourceUriBuilder resourceUriBuilder,
            ResourceHandler resourceHandler,
            ObjectMapper objectMapper) {
        this.openApiRegistry = openApiRegistry;
        this.namingStrategy = namingStrategy;
        this.resourceUriBuilder = resourceUriBuilder;
        this.resourceHandler = resourceHandler;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves all MCP resource specifications derived from OpenAPI operations marked with the
     * {@code x-mcp-resource} or {@code x-mcp-app-resource} vendor extensions.
     *
     * @return a list of registered resources, one for each marked operation
     * @throws ResourceRegistrationException if a marked operation is not a {@code GET} operation, if a
     *                                       resource name cannot be determined, or if an MCP app resource is
     *                                       configured invalidly (parameters present, duplicate {@code ui://}
     *                                       URI, malformed extension)
     */
    public List<RegisteredResource> getResources() {
        var openApi = openApiRegistry.openApi();
        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            this.registeredResourcesCache = List.of();
            this.appResourceUrisCache = Set.of();
            return registeredResourcesCache;
        }

        var appResourceUris = new LinkedHashMap<String, FullOperation>();
        var registeredResources = openApi.getPaths().entrySet().stream()
                .flatMap(pathEntry -> pathEntry.getValue().readOperationsMap().entrySet().stream()
                        .map(operationEntry -> new FullOperation(
                                pathEntry.getKey(), operationEntry.getKey(), operationEntry.getValue(), openApi)))
                .filter(fullOperation -> Spec.isResourceOperation(fullOperation.operation()))
                .map(fullOperation -> toRegisteredResource(fullOperation, appResourceUris))
                .toList();

        this.registeredResourcesCache = List.copyOf(registeredResources);
        this.appResourceUrisCache = Set.copyOf(appResourceUris.keySet());
        return registeredResources;
    }

    /**
     * Returns the {@code ui://} URIs of the MCP app resources registered by the last {@link #getResources()}
     * call, for consumers that validate references against the registered set.
     */
    public Set<String> getRegisteredAppResourceUris() {
        return appResourceUrisCache;
    }

    private RegisteredResource toRegisteredResource(
            FullOperation fullOperation, Map<String, FullOperation> appResourceUris) {
        if (Spec.isAppResourceOperation(fullOperation.operation())) {
            return toAppRegisteredResource(fullOperation, appResourceUris);
        }
        return toPlainRegisteredResource(fullOperation);
    }

    private RegisteredResource toPlainRegisteredResource(FullOperation fullOperation) {
        if (fullOperation.method() != PathItem.HttpMethod.GET) {
            throw ResourceRegistrationException.becauseNonGetMethodMarkedAsResource(fullOperation);
        }

        var resourceName = determineResourceName(fullOperation);
        var title = resolveTitle(fullOperation, resourceName);
        var description = fullOperation.operation().getDescription();
        var mimeType = resolveMimeType(fullOperation);
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (context, request) ->
                        resourceHandler.handleResourceRead(fullOperation, resourceName, mimeType, request, context);

        if (resourceUriBuilder.isTemplate(fullOperation)) {
            var resourceTemplate = McpSchema.ResourceTemplate.builder()
                    .uriTemplate(resourceUriBuilder.build(fullOperation))
                    .name(resourceName)
                    .title(title)
                    .description(description)
                    .mimeType(mimeType)
                    .build();
            return RegisteredResource.ofResourceTemplate(resourceTemplate, handler);
        }

        var resource = McpSchema.Resource.builder()
                .uri(resourceUriBuilder.build(fullOperation))
                .name(resourceName)
                .title(title)
                .description(description)
                .mimeType(mimeType)
                .build();
        return RegisteredResource.ofResource(resource, handler);
    }

    private RegisteredResource toAppRegisteredResource(
            FullOperation fullOperation, Map<String, FullOperation> appResourceUris) {
        if (fullOperation.method() != PathItem.HttpMethod.GET) {
            throw ResourceRegistrationException.becauseNonGetMethodMarkedAsAppResource(fullOperation);
        }
        if (Spec.isAppToolOperation(fullOperation.operation())) {
            throw ResourceRegistrationException.becauseMarkedAsAppResourceAndAppTool(fullOperation);
        }
        if (resourceUriBuilder.isTemplate(fullOperation)) {
            throw ResourceRegistrationException.becauseAppResourceOperationHasParameters(fullOperation);
        }

        var appResourceExtension = parseAppResourceExtension(fullOperation);
        var uri = resolveAppResourceUri(fullOperation, appResourceExtension);
        validateAppResourceUriIsUnique(uri, fullOperation, appResourceUris);

        var resourceName = determineResourceName(fullOperation);
        var title = resolveTitle(fullOperation, resourceName);
        var description = fullOperation.operation().getDescription();
        var uiMeta = AppMeta.resourceUiMeta(appResourceExtension);

        var resourceBuilder = McpSchema.Resource.builder()
                .uri(uri)
                .name(resourceName)
                .title(title)
                .description(description)
                .mimeType(AppMeta.MIME_TYPE);
        if (!uiMeta.isEmpty()) {
            resourceBuilder.meta(Map.of(AppMeta.UI_META_KEY, uiMeta));
        }

        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler =
                (context, request) -> resourceHandler.handleResourceRead(
                        fullOperation, resourceName, AppMeta.MIME_TYPE, request, context, appResourceExtension);
        return RegisteredResource.ofResource(resourceBuilder.build(), handler);
    }

    private AppResourceExtension parseAppResourceExtension(FullOperation fullOperation) {
        var extensionValue = fullOperation.operation().getExtensions().get(Spec.MCP_APP_RESOURCE_EXTENSION);
        if (extensionValue == null || Boolean.TRUE.equals(extensionValue)) {
            return AppResourceExtension.defaults();
        }
        try {
            return objectMapper.convertValue(extensionValue, AppResourceExtension.class);
        } catch (JacksonException exception) {
            throw ResourceRegistrationException.becauseInvalidAppResourceExtension(fullOperation, exception);
        }
    }

    private String resolveAppResourceUri(FullOperation fullOperation, AppResourceExtension appResourceExtension) {
        if (appResourceExtension.uri() != null) {
            return appResourceExtension.uri();
        }
        var path = fullOperation.path();
        return AppMeta.URI_SCHEME + (path.startsWith("/") ? path.substring(1) : path);
    }

    private void validateAppResourceUriIsUnique(
            String uri, FullOperation fullOperation, Map<String, FullOperation> appResourceUris) {
        var previous = appResourceUris.putIfAbsent(uri, fullOperation);
        if (previous != null) {
            throw ResourceRegistrationException.becauseDuplicateAppResourceUri(uri, previous, fullOperation);
        }
    }

    private String determineResourceName(FullOperation fullOperation) {
        try {
            return namingStrategy.name(fullOperation);
        } catch (RuntimeException exception) {
            throw ResourceRegistrationException.becauseNameCannotBeDetermined(fullOperation, exception);
        }
    }

    private String resolveTitle(FullOperation fullOperation, String resourceName) {
        var summary = fullOperation.operation().getSummary();
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return resourceName;
    }

    /**
     * Resolves the MIME type from the first successful response declaring content. {@code 200} is preferred, then any
     * other explicit {@code 2xx} code in ascending order, then the {@code 2XX} range key. Falls back to
     * {@code application/json} when no successful response declares content.
     */
    private String resolveMimeType(FullOperation fullOperation) {
        var responses = fullOperation.operation().getResponses();
        if (responses == null) {
            return DEFAULT_MIME_TYPE;
        }
        return responses.entrySet().stream()
                .filter(entry -> successResponsePriority(entry.getKey()) >= 0)
                .filter(entry -> entry.getValue().getContent() != null
                        && !entry.getValue().getContent().isEmpty())
                .min(Comparator.comparingInt(entry -> successResponsePriority(entry.getKey())))
                .map(entry -> entry.getValue().getContent().keySet().iterator().next())
                .orElse(DEFAULT_MIME_TYPE);
    }

    private int successResponsePriority(String responseCode) {
        if (PREFERRED_SUCCESS_RESPONSE_CODE.equals(responseCode)) {
            return 0;
        }
        if (SUCCESS_RESPONSE_RANGE.equalsIgnoreCase(responseCode)) {
            return 1000;
        }
        if (responseCode.length() == 3
                && responseCode.charAt(0) == '2'
                && responseCode.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(responseCode);
        }
        return -1;
    }

    public List<RegisteredResource> getRegisteredResourcesCache() {
        return registeredResourcesCache;
    }
}
