package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
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
        var resourceTimer = metricService.startResourceTimer();

        try {
            var credential = credentialProvider.provide(context);
            var responseEntity = executeHttpRequest(fullOperation, request, context, credential);

            resourceTimer.timeReadCall(resourceName, responseEntity.getStatusCode());
            metricService.recordResourceReadCall(resourceName, responseEntity.getStatusCode());

            var responseBody = responseEntity.getBody() != null ? responseEntity.getBody() : "";
            var contents = new McpSchema.TextResourceContents(request.uri(), mimeType, responseBody);
            return new McpSchema.ReadResourceResult(List.of(contents));
        } catch (HttpStatusCodeException exception) {
            resourceTimer.timeReadCall(resourceName, exception.getStatusCode());
            metricService.recordResourceReadCall(resourceName, exception.getStatusCode());
            LOGGER.debug(
                    "HTTP status code {} while reading resource '{}': {}",
                    exception.getStatusCode(),
                    resourceName,
                    exception.getResponseBodyAsString());
            throw ResourceReadException.becauseBackendCallFailed(resourceName, fullOperation.path(), exception);
        } catch (RuntimeException exception) {
            resourceTimer.timeReadCall(resourceName, HttpStatus.BAD_GATEWAY);
            metricService.recordResourceReadCall(resourceName, HttpStatus.BAD_GATEWAY);
            LOGGER.error("Error while reading resource '{}': {}", resourceName, exception.getMessage(), exception);
            throw ResourceReadException.becauseBackendCallFailed(resourceName, fullOperation.path(), exception);
        }
    }

    private ResponseEntity<String> executeHttpRequest(
            FullOperation fullOperation,
            McpSchema.ReadResourceRequest request,
            McpRequestContext context,
            Optional<String> credential) {
        var pathVariables = resourceUriBuilder.matchPathVariables(fullOperation, request.uri());
        var queryParameters = resourceUriBuilder.extractQueryParameters(request.uri());

        var spec = restClient.get().uri(uriBuilder -> {
            var builder = uriBuilder.path(fullOperation.path());
            queryParameters.forEach(builder::queryParam);
            return builder.build(pathVariables);
        });

        credential.ifPresent(authHeader -> spec.header(HttpHeaders.AUTHORIZATION, authHeader));

        var enrichedSpec = enricherChain.enrich(spec, context);
        return enrichedSpec.retrieve().toEntity(String.class);
    }
}
