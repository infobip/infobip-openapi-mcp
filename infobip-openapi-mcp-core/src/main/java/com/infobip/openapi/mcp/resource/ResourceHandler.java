package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import com.infobip.openapi.mcp.util.PlusAwareUriEncoder;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    private final RestClient restClient;
    private final CredentialProvider credentialProvider;
    private final ApiRequestEnricherChain enricherChain;
    private final MetricService metricService;
    private final ResourceUriBuilder resourceUriBuilder;
    private final ResourceContentsConverter resourceContentsConverter;

    public ResourceHandler(
            RestClient restClient,
            CredentialProvider credentialProvider,
            ApiRequestEnricherChain enricherChain,
            MetricService metricService,
            ResourceUriBuilder resourceUriBuilder,
            ResourceContentsConverter resourceContentsConverter) {
        this.restClient = restClient;
        this.credentialProvider = credentialProvider;
        this.enricherChain = enricherChain;
        this.metricService = metricService;
        this.resourceUriBuilder = resourceUriBuilder;
        this.resourceContentsConverter = resourceContentsConverter;
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
            var contents = convert(fullOperation, resourceName, mimeType, request, context, response);

            resourceCallTimer.timeResourceCall(resourceName, false);
            return new McpSchema.ReadResourceResult(List.of(contents));
        } catch (RuntimeException exception) {
            resourceCallTimer.timeResourceCall(resourceName, true);
            throw exception;
        }
    }

    private McpSchema.ResourceContents convert(
            FullOperation fullOperation,
            String resourceName,
            String mimeType,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context,
            ResponseEntity<byte[]> response) {
        var resourceResponse = new ResourceResponse(
                request.uri(),
                resourceName,
                mimeType,
                fullOperation,
                response.getStatusCode(),
                response.getHeaders(),
                response.getBody() != null ? response.getBody() : new byte[0]);
        try {
            return resourceContentsConverter.convert(resourceResponse, context);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to convert response of resource '{}' into resource contents: {}",
                    resourceName,
                    exception.getMessage(),
                    exception);
            throw ResourceReadException.becauseContentsConversionFailed(resourceName, exception);
        }
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

        // Query values are bound as URI variables rather than inlined into the template, so that '{...}' inside a
        // value is never expanded as a template variable. Query and path values are pre-encoded here rather than
        // relying on the RestClient's URI builder, since the downstream RestClient is configured with
        // EncodingMode.NONE.
        var uriVariables = new HashMap<String, Object>();
        pathVariables.forEach((name, value) -> uriVariables.put(name, PlusAwareUriEncoder.encodePathSegment(value)));
        var spec = restClient.get().uri(uriBuilder -> {
            var builder = uriBuilder.path(fullOperation.path());
            var index = 0;
            for (var queryParameter : queryParameters.entrySet()) {
                var variableName = QUERY_VARIABLE_PREFIX + index++;
                builder.queryParam(
                        PlusAwareUriEncoder.encodeQueryParam(queryParameter.getKey()), "{" + variableName + "}");
                uriVariables.put(variableName, PlusAwareUriEncoder.encodeQueryParam(queryParameter.getValue()));
            }
            return builder.build(uriVariables);
        });

        credential.ifPresent(authHeader -> spec.header(HttpHeaders.AUTHORIZATION, authHeader));

        var enrichedSpec = enricherChain.enrich(spec, context);
        return enrichedSpec.retrieve().toEntity(byte[].class);
    }
}
