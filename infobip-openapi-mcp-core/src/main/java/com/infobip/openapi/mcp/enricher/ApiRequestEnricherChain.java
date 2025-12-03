package com.infobip.openapi.mcp.enricher;

import com.infobip.openapi.mcp.McpRequestContext;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * A chain of API request enrichers that applies each enricher in sequence to outbound HTTP requests.
 * <p>
 * This class centralizes the execution logic for applying multiple {@link ApiRequestEnricher}s
 * to REST client requests. It follows the same pattern as {@code OpenApiFilterChain}, providing
 * consistent error handling and logging across all enricher applications.
 * </p>
 *
 * <h3>Execution Behavior:</h3>
 * <ul>
 *   <li>Each enricher receives the result of the previous enricher's modifications</li>
 *   <li>Errors in individual enrichers are logged but do not stop the chain (non-blocking)</li>
 *   <li>If an enricher throws an exception, the chain continues with the remaining enrichers</li>
 * </ul>
 *
 * <h3>Error Handling:</h3>
 * <p>
 * The chain uses a <strong>non-blocking</strong> error handling strategy. If an enricher fails:
 * </p>
 * <ol>
 *   <li>A warning is logged with the enricher name and error message</li>
 *   <li>The exception is caught and the chain continues</li>
 *   <li>The next enricher receives the spec from before the failed enricher</li>
 * </ol>
 * <p>
 * This ensures that one failing enricher (e.g., due to missing request context) doesn't prevent
 * other enrichers from executing. For example, if {@code XForwardedForEnricher} fails, the
 * {@code UserAgentEnricher} will still run.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Component
 * public class ToolHandler {
 *     private final ApiRequestEnricherChain enricherChain;
 *
 *     public void makeRequest(McpRequestContext context) {
 *         var spec = restClient.get().uri("/api/endpoint");
 *         spec = enricherChain.enrich(spec, context);
 *         var response = spec.retrieve().body(String.class);
 *     }
 * }
 * }</pre>
 *
 * <h3>Where Applied:</h3>
 * <ul>
 *   <li>{@code ToolHandler} - Enriches downstream API calls when MCP tools are invoked</li>
 *   <li>{@code InitialAuthenticationFilter} - Enriches auth validation requests</li>
 *   <li>{@code OAuthRouter} - Enriches OAuth well-known endpoint proxy calls</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is thread-safe and stateless. The enricher list is immutable after construction,
 * and no state is maintained between invocations.
 * </p>
 *
 * @see ApiRequestEnricher
 * @see McpRequestContext
 */
@NullMarked
public class ApiRequestEnricherChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRequestEnricherChain.class);

    private final List<ApiRequestEnricher> enrichers;

    /**
     * Creates a new enricher chain with the provided list of enrichers.
     *
     * @param enrichers the list of enrichers to apply, in order
     */
    public ApiRequestEnricherChain(List<ApiRequestEnricher> enrichers) {
        this.enrichers = List.copyOf(enrichers);
    }

    /**
     * Applies all enrichers in the chain to the provided REST client request specification.
     * <p>
     * Each enricher is applied sequentially, with each receiving the result of the previous
     * enricher's modifications. If an enricher throws an exception, it is logged as a warning
     * and the chain continues with the remaining enrichers.
     * </p>
     *
     * @param spec    the REST client request specification to enrich
     * @param context the MCP request context containing the HTTP servlet request and other metadata
     * @return the enriched request specification after all enrichers have been applied
     */
    public RestClient.RequestHeadersSpec<?> enrich(RestClient.RequestHeadersSpec<?> spec, McpRequestContext context) {
        var enrichedSpec = spec;

        for (var enricher : enrichers) {
            try {
                LOGGER.debug("Applying enricher: {}", enricher.name());
                enrichedSpec = enricher.enrich(enrichedSpec, context);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Enricher {} failed during request enrichment: {}. Continuing with remaining enrichers.",
                        enricher.name(),
                        exception.getMessage());
            }
        }

        return enrichedSpec;
    }
}
