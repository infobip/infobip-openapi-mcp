package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategy;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.PathItem;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Registry for converting OpenAPI operations marked with the {@code x-mcp-resource} vendor extension into MCP
 * resource specifications.
 * <p>
 * Each marked operation becomes either a static MCP {@code Resource} (no path or query parameters) or a
 * {@code ResourceTemplate} (path and/or query parameters present). The {@code uri}/{@code uriTemplate},
 * {@code name}, {@code title}, {@code description} and {@code mimeType} are all derived from the OpenAPI
 * operation.
 */
public class ResourceRegistry {

    private static final String DEFAULT_MIME_TYPE = "application/json";
    private static final String PREFERRED_SUCCESS_RESPONSE_CODE = "200";
    private static final String SUCCESS_RESPONSE_RANGE = "2XX";

    private final OpenApiRegistry openApiRegistry;
    private final NamingStrategy namingStrategy;
    private final ResourceUriBuilder resourceUriBuilder;
    private final ResourceHandler resourceHandler;

    private List<RegisteredResource> registeredResourcesCache = List.of();

    public ResourceRegistry(
            OpenApiRegistry openApiRegistry,
            NamingStrategy namingStrategy,
            ResourceUriBuilder resourceUriBuilder,
            ResourceHandler resourceHandler) {
        this.openApiRegistry = openApiRegistry;
        this.namingStrategy = namingStrategy;
        this.resourceUriBuilder = resourceUriBuilder;
        this.resourceHandler = resourceHandler;
    }

    /**
     * Retrieves all MCP resource specifications derived from OpenAPI operations marked with the
     * {@code x-mcp-resource} vendor extension.
     *
     * @return a list of registered resources, one for each marked operation
     * @throws ResourceRegistrationException if a marked operation is not a {@code GET} operation, or if a
     *                                        resource name cannot be determined
     */
    public List<RegisteredResource> getResources() {
        var openApi = openApiRegistry.openApi();
        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            return List.of();
        }

        var registeredResources = openApi.getPaths().entrySet().stream()
                .flatMap(pathEntry -> pathEntry.getValue().readOperationsMap().entrySet().stream()
                        .map(operationEntry -> new FullOperation(
                                pathEntry.getKey(), operationEntry.getKey(), operationEntry.getValue(), openApi)))
                .filter(fullOperation -> Spec.isResourceOperation(fullOperation.operation()))
                .map(this::toRegisteredResource)
                .toList();

        this.registeredResourcesCache = List.copyOf(registeredResources);
        return registeredResources;
    }

    private RegisteredResource toRegisteredResource(FullOperation fullOperation) {
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
