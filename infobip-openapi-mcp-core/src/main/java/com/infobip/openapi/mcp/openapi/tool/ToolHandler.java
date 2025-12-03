package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Handles the execution of tool calls by calling the downstream HTTP APIs as defined in the OpenAPI specification.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Mapping MCP tool arguments to OpenAPI operation parameters</li>
 *   <li><b>Explicitly forwarding Authorization header</b> (not via enrichers)</li>
 *   <li>Applying enrichers for observability headers (X-Forwarded-For, X-Forwarded-Host, User-Agent, etc.)</li>
 *   <li>Executing HTTP requests to downstream APIs</li>
 *   <li>Converting responses to MCP tool results</li>
 *   <li>Handling errors and mapping them to MCP error responses</li>
 * </ul>
 * </p>
 *
 * <h3>Authorization Handling:</h3>
 * <p>
 * Authorization header forwarding is <b>intentionally explicit</b> in this class
 * rather than delegated to enrichers. This design decision ensures:
 * </p>
 * <ul>
 *   <li>Security logic is obvious and easy to audit</li>
 *   <li>Auth failures are immediate and clear</li>
 *   <li>No silent degradation of security</li>
 *   <li>Easy to understand for security reviews</li>
 * </ul>
 *
 * @see ApiRequestEnricherChain
 * @see McpRequestContext
 */
@NullMarked
public class ToolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolHandler.class);

    private static final MediaType PREFERRED_ACCEPT_MEDIA_TYPE = MediaType.APPLICATION_JSON;

    private static final String DEFAULT_SUCCESS_RESPONSE = "{\"message\":\"Tool call completed successfully\"}";

    private final RestClient restClient;
    private final ErrorModelWriter errorModelWriter;
    private final JsonDoubleSerializationCorrector serializationCorrector;
    private final OpenApiMcpProperties properties;
    private final ApiRequestEnricherChain enricherChain;
    private final MetricService metricService;

    public ToolHandler(
            RestClient restClient,
            ErrorModelWriter errorModelWriter,
            OpenApiMcpProperties properties,
            ApiRequestEnricherChain enricherChain,
            MetricService metricService) {
        this.restClient = restClient;
        this.errorModelWriter = errorModelWriter;
        this.properties = properties;
        this.enricherChain = enricherChain;
        this.metricService = metricService;
        this.serializationCorrector = new JsonDoubleSerializationCorrector();
    }

    /**
     * Handles the tool call by making an HTTP request to the downstream API.
     *
     * @param fullOperation         The full operation details including path, method, and operation object.
     * @param decomposedRequestData The decomposed schema containing parameters and request body.
     * @return The result of the tool call including response body and error status.
     */
    public McpSchema.CallToolResult handleToolCall(
            FullOperation fullOperation, DecomposedRequestData decomposedRequestData, McpRequestContext context) {
        metricService.recordToolCall(fullOperation);

        var httpCallTimer = metricService.startTimer();
        var toolCallTimer = metricService.startTimer();

        try {
            var response = executeHttpRequest(fullOperation, decomposedRequestData, context);
            httpCallTimer.timeApiCall(fullOperation, response.getStatusCode());
            metricService.recordApiCall(fullOperation, response.getStatusCode());

            var responseBody = getResponseBodyOrDefault(response.getBody());

            toolCallTimer.timeToolCall(fullOperation, response.getStatusCode().isError());
            return new McpSchema.CallToolResult(
                    responseBody, response.getStatusCode().isError());
        } catch (HttpStatusCodeException exception) {
            httpCallTimer.timeApiCall(fullOperation, exception.getStatusCode());

            var correctedRequestData = correctRequestDataIfPossible(exception, decomposedRequestData);
            if (correctedRequestData.isPresent()) {
                var httpCallRetryTimer = metricService.startTimer();

                try {
                    var retryResponse = executeHttpRequest(fullOperation, correctedRequestData.get(), context);
                    httpCallRetryTimer.timeApiCall(fullOperation, retryResponse.getStatusCode());
                    metricService.recordApiCall(fullOperation, retryResponse.getStatusCode());

                    var responseBody = getResponseBodyOrDefault(retryResponse.getBody());

                    toolCallTimer.timeToolCall(
                            fullOperation, retryResponse.getStatusCode().isError());
                    return new McpSchema.CallToolResult(
                            responseBody, retryResponse.getStatusCode().isError());
                } catch (HttpStatusCodeException retryException) {
                    httpCallRetryTimer.timeApiCall(fullOperation, retryException.getStatusCode());
                    metricService.recordApiCall(fullOperation, retryException.getStatusCode());

                    LOGGER.debug(
                            "Retry also failed with status {}: {}",
                            retryException.getStatusCode(),
                            retryException.getResponseBodyAsString());

                    toolCallTimer.timeToolCall(
                            fullOperation, retryException.getStatusCode().isError());
                    return new McpSchema.CallToolResult(retryException.getResponseBodyAsString(), true);
                } catch (RuntimeException retryException) {
                    httpCallRetryTimer.timeApiCall(fullOperation, HttpStatus.BAD_GATEWAY);
                    metricService.recordApiCall(fullOperation, HttpStatus.BAD_GATEWAY);

                    LOGGER.error(
                            "Retry failed with network error: {}. Downstream request failed.",
                            retryException.getMessage(),
                            retryException);

                    toolCallTimer.timeToolCall(fullOperation, true);
                    return new McpSchema.CallToolResult(
                            errorModelWriter.writeErrorModelAsJson(HttpStatus.BAD_GATEWAY), true);
                }
            }

            LOGGER.debug("HTTP status code {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            metricService.recordApiCall(fullOperation, exception.getStatusCode());
            toolCallTimer.timeToolCall(fullOperation, true);
            return new McpSchema.CallToolResult(exception.getResponseBodyAsString(), true);
        } catch (RuntimeException e) {
            httpCallTimer.timeApiCall(fullOperation, HttpStatus.BAD_GATEWAY);
            metricService.recordApiCall(fullOperation, HttpStatus.BAD_GATEWAY);
            LOGGER.error("Error while calling tool: {}. Downstream request failed.", e.getMessage(), e);

            toolCallTimer.timeToolCall(fullOperation, true);
            return new McpSchema.CallToolResult(errorModelWriter.writeErrorModelAsJson(HttpStatus.BAD_GATEWAY), true);
        }
    }

    private String getResponseBodyOrDefault(@Nullable String responseBody) {
        return responseBody != null ? responseBody : DEFAULT_SUCCESS_RESPONSE;
    }

    /**
     * Attempts to correct JSON double serialization issues in the request data if conditions are met.
     *
     * <p>This method will only attempt correction if:
     * <ul>
     *   <li>JSON double serialization mitigation is enabled in configuration</li>
     *   <li>The HTTP exception status is 400 Bad Request</li>
     *   <li>The request body exists and has JSON-compatible content type</li>
     *   <li>The serialization detector successfully identifies and corrects double serialization</li>
     * </ul>
     *
     * @param exception             the HTTP status code exception that triggered the retry attempt
     * @param decomposedRequestData the original request data to potentially correct
     * @return an Optional containing corrected request data if correction was possible and successful,
     * or empty if no correction was needed or possible
     */
    private Optional<DecomposedRequestData> correctRequestDataIfPossible(
            HttpStatusCodeException exception, DecomposedRequestData decomposedRequestData) {
        if (!shouldAttemptCorrection(exception)) {
            return Optional.empty();
        }

        return decomposedRequestData
                .resolveRequestBody()
                .flatMap(serializationCorrector::correctIfDetected)
                .map(correctedBody -> DecomposedRequestData.withParametersAndBodyContent(
                        decomposedRequestData.parametersByType(), correctedBody.content()));
    }

    /**
     * Determines if we should attempt JSON double serialization correction based on configuration and exception status.
     *
     * @param exception the HTTP status code exception that occurred
     * @return true if correction should be attempted, false otherwise
     */
    private boolean shouldAttemptCorrection(HttpStatusCodeException exception) {
        return properties.tools().jsonDoubleSerializationMitigation()
                && exception.getStatusCode() == HttpStatus.BAD_REQUEST;
    }

    /**
     * Executes the HTTP request with the given parameters.
     * <p>
     * <b>Authorization Handling:</b> The Authorization header is explicitly forwarded
     * from the original HTTP request before enrichers are applied. This is intentional
     * to make authentication handling obvious and auditable.
     * </p>
     *
     * @param fullOperation         the OpenAPI operation to execute
     * @param decomposedRequestData the request parameters and body
     * @param context               the MCP request context containing HTTP request and session info
     * @return the response from the downstream API
     * @throws HttpStatusCodeException if the API returns an error status
     */
    private org.springframework.http.ResponseEntity<String> executeHttpRequest(
            FullOperation fullOperation, DecomposedRequestData decomposedRequestData, McpRequestContext context) {
        var spec = restClient
                .method(HttpMethod.valueOf(fullOperation.method().name()))
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(fullOperation.path());
                    decomposedRequestData
                            .parametersByType()
                            .query()
                            .forEach((name, value) -> addQueryParameter(builder, name, value));
                    return builder.build(
                            decomposedRequestData.parametersByType().path());
                });

        decomposedRequestData.resolveRequestBody().ifPresent(body -> {
            spec.body(body.content());
            spec.contentType(body.targetContentType());
        });

        decomposedRequestData
                .parametersByType()
                .header()
                .forEach((header, headerValue) -> addHeaderParameter(spec, header, headerValue));

        decomposedRequestData
                .parametersByType()
                .cookie()
                .forEach((cookie, cookieValue) -> addCookieParameter(spec, cookie, cookieValue));

        // Authorization is explicitly handled here (not via enrichers) because it's a critical security concern
        // that must be obvious and not abstracted away in enrichers.
        var request = context.httpServletRequest();
        if (request != null) {
            var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && !authHeader.isBlank()) {
                spec.header(HttpHeaders.AUTHORIZATION, authHeader);
                LOGGER.debug("Forwarded Authorization header to downstream API");
            }
        }

        var enrichedSpec = enricherChain.enrich(spec, context);

        // Add Accept header if not already present
        enrichedSpec.headers(
                headers -> headers.addIfAbsent(HttpHeaders.ACCEPT, PREFERRED_ACCEPT_MEDIA_TYPE.toString()));

        return enrichedSpec.retrieve().toEntity(String.class);
    }

    /**
     * Adds a query parameter to the URI builder.
     * Currently only OpenAPI default "form" style query parameters are supported with "explode" set to true.
     */
    private void addQueryParameter(UriBuilder uriBuilder, String name, Object value) {
        if (value instanceof Iterable<?> iterableValue) {
            for (var item : iterableValue) {
                if (item != null) {
                    uriBuilder.queryParam(name, item.toString());
                }
            }
        } else {
            uriBuilder.queryParam(name, value.toString());
        }
    }

    /**
     * Adds a header parameter to the request specification.
     * Currently only OpenAPI default "simple" style header parameters are supported with "explode" set to false.
     * The values are comma-separated if multiple values are provided.
     */
    private void addHeaderParameter(RestClient.RequestHeadersSpec<?> spec, String name, Object value) {
        if (value instanceof Iterable<?> iterableValue) {
            var combinedValue = new StringBuilder();
            for (var item : iterableValue) {
                if (item != null) {
                    if (!combinedValue.isEmpty()) {
                        combinedValue.append(","); // Comma separator for multiple values
                    }
                    combinedValue.append(item);
                }
            }
            if (combinedValue.isEmpty()) {
                return;
            }
            spec.header(name, combinedValue.toString());
            return;
        }
        spec.header(name, value.toString());
    }

    /**
     * Adds a cookie parameter to the URI request specification.
     * Currently only OpenAPI default "form" style cookie parameters are supported with "explode" set to true.
     */
    private void addCookieParameter(RestClient.RequestHeadersSpec<?> spec, String name, Object value) {
        if (value instanceof Iterable<?> iterableValue) {
            for (var item : iterableValue) {
                if (item != null) {
                    spec.cookie(name, item.toString());
                }
            }
        } else {
            spec.cookie(name, value.toString());
        }
    }
}
