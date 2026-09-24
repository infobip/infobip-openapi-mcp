package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Handles MCP resource reads by calling the downstream HTTP API that backs an operation marked with the
 * {@code x-mcp-resource} vendor extension.
 */
@NullMarked
public class ResourceHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceHandler.class);
    private static final String QUERY_VARIABLE_PREFIX = "__query";
    private static final Set<String> TEXTUAL_SUBTYPES = Set.of(
            "json",
            "xml",
            "yaml",
            "x-yaml",
            "javascript",
            "ecmascript",
            "graphql",
            "sql",
            "x-ndjson",
            "x-www-form-urlencoded");

    private final RestClient restClient;
    private final CredentialProvider credentialProvider;
    private final ApiRequestEnricherChain enricherChain;
    private final MetricService metricService;
    private final ResourceUriBuilder resourceUriBuilder;

    public ResourceHandler(
            RestClient restClient,
            CredentialProvider credentialProvider,
            ApiRequestEnricherChain enricherChain,
            MetricService metricService,
            ResourceUriBuilder resourceUriBuilder) {
        this.restClient = restClient;
        this.credentialProvider = credentialProvider;
        this.enricherChain = enricherChain;
        this.metricService = metricService;
        this.resourceUriBuilder = resourceUriBuilder;
    }

    public McpSchema.ReadResourceResult handleResourceRead(
            FullOperation fullOperation,
            String resourceName,
            String mimeType,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context) {
        metricService.recordResourceCall(resourceName);
        var resourceCallTimer = metricService.startResourceTimer();

        try {
            Optional<String> credential;
            try {
                credential = credentialProvider.provide(context);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to provide credential for resource '{}': {}", resourceName, exception.getMessage());
                throw ResourceReadException.becauseBackendCallFailed(resourceName, fullOperation.path(), exception);
            }

            var response = readFromBackend(fullOperation, resourceName, request, context, credential);
            var contents = toResourceContents(request.uri(), mimeType, response);

            resourceCallTimer.timeResourceCall(resourceName, false);
            return new McpSchema.ReadResourceResult(List.of(contents));
        } catch (RuntimeException exception) {
            resourceCallTimer.timeResourceCall(resourceName, true);
            throw exception;
        }
    }

    /**
     * Converts the backend response into MCP resource contents. The media type is taken from the response
     * {@code Content-Type} header, falling back to the MIME type declared for the resource. Textual media types are
     * returned as {@link McpSchema.TextResourceContents} decoded with the response charset (UTF-8 by default); any other
     * media type is returned as base64 encoded {@link McpSchema.BlobResourceContents}.
     */
    private McpSchema.ResourceContents toResourceContents(
            String uri, String declaredMimeType, ResponseEntity<byte[]> response) {
        var body = response.getBody() != null ? response.getBody() : new byte[0];
        var mediaType = resolveMediaType(response.getHeaders().getContentType(), declaredMimeType);
        if (mediaType == null) {
            return new McpSchema.TextResourceContents(uri, declaredMimeType, new String(body, StandardCharsets.UTF_8));
        }

        var mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        if (isTextual(mediaType)) {
            var charset = mediaType.getCharset() != null ? mediaType.getCharset() : StandardCharsets.UTF_8;
            return new McpSchema.TextResourceContents(uri, mimeType, new String(body, charset));
        }
        return new McpSchema.BlobResourceContents(
                uri, mimeType, Base64.getEncoder().encodeToString(body));
    }

    private @Nullable MediaType resolveMediaType(@Nullable MediaType responseContentType, String declaredMimeType) {
        if (responseContentType != null) {
            return responseContentType;
        }
        try {
            return MediaType.parseMediaType(declaredMimeType);
        } catch (InvalidMediaTypeException exception) {
            LOGGER.debug(
                    "Declared MIME type '{}' is not a valid media type, treating content as text", declaredMimeType);
            return null;
        }
    }

    private boolean isTextual(MediaType mediaType) {
        if ("text".equals(mediaType.getType())) {
            return true;
        }
        var suffix = mediaType.getSubtypeSuffix();
        if (suffix != null && TEXTUAL_SUBTYPES.contains(suffix)) {
            return true;
        }
        return "application".equals(mediaType.getType()) && TEXTUAL_SUBTYPES.contains(mediaType.getSubtype());
    }

    private ResponseEntity<byte[]> readFromBackend(
            FullOperation fullOperation,
            String resourceName,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context,
            Optional<String> credential) {
        var readCallTimer = metricService.startResourceTimer();

        try {
            var responseEntity = executeHttpRequest(fullOperation, request, context, credential);

            readCallTimer.timeReadCall(resourceName, responseEntity.getStatusCode());
            metricService.recordResourceReadCall(resourceName, responseEntity.getStatusCode());

            return responseEntity;
        } catch (HttpStatusCodeException exception) {
            readCallTimer.timeReadCall(resourceName, exception.getStatusCode());
            metricService.recordResourceReadCall(resourceName, exception.getStatusCode());
            LOGGER.debug(
                    "HTTP status code {} while reading resource '{}': {}",
                    exception.getStatusCode(),
                    resourceName,
                    exception.getResponseBodyAsString());
            throw ResourceReadException.becauseBackendCallFailed(resourceName, fullOperation.path(), exception);
        } catch (RuntimeException exception) {
            readCallTimer.timeReadCall(resourceName, HttpStatus.BAD_GATEWAY);
            metricService.recordResourceReadCall(resourceName, HttpStatus.BAD_GATEWAY);
            LOGGER.error("Error while reading resource '{}': {}", resourceName, exception.getMessage(), exception);
            throw ResourceReadException.becauseBackendCallFailed(resourceName, fullOperation.path(), exception);
        }
    }

    private ResponseEntity<byte[]> executeHttpRequest(
            FullOperation fullOperation,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context,
            Optional<String> credential) {
        var pathVariables = resourceUriBuilder.matchPathVariables(fullOperation, request.uri());
        var queryParameters = resourceUriBuilder.extractQueryParameters(fullOperation, request.uri());

        // Query values are bound as URI variables rather than inlined into the template, so that they are fully
        // encoded (e.g. a literal '+' becomes %2B) and '{...}' inside a value is never expanded as a template variable.
        var uriVariables = new HashMap<String, Object>(pathVariables);
        var spec = restClient.get().uri(uriBuilder -> {
            var builder = uriBuilder.path(fullOperation.path());
            var index = 0;
            for (var queryParameter : queryParameters.entrySet()) {
                var variableName = QUERY_VARIABLE_PREFIX + index++;
                builder.queryParam(queryParameter.getKey(), "{" + variableName + "}");
                uriVariables.put(variableName, queryParameter.getValue());
            }
            return builder.build(uriVariables);
        });

        credential.ifPresent(authHeader -> spec.header(HttpHeaders.AUTHORIZATION, authHeader));

        var enrichedSpec = enricherChain.enrich(spec, context);
        return enrichedSpec.retrieve().toEntity(byte[].class);
    }
}
