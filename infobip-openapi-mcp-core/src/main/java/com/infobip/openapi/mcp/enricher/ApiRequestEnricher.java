package com.infobip.openapi.mcp.enricher;

import com.infobip.openapi.mcp.McpRequestContext;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

/**
 * Strategy interface for enriching API requests before they are sent to downstream services.
 * <p>
 * This interface defines the contract for request enrichers that can modify the
 * {@link RestClient.RequestHeadersSpec} to add additional headers, metadata, or other
 * request modifications before the HTTP call is executed.
 * </p>
 *
 * <h3>Usage:</h3>
 * <p>
 * Implementations of this interface are applied to all downstream API calls. Multiple enrichers
 * can be composed to apply different enrichment strategies in sequence. All beans implementing
 * this interface are automatically discovered by Spring and applied through dependency injection.
 * </p>
 *
 * <h3>Execution Order:</h3>
 * <p>
 * This interface extends {@link Ordered} to ensure enrichers execute in a predictable sequence.
 * Implementations should define a public ORDER constant and return it from {@link #getOrder()}.
 * Lower values execute first. The default order is {@link Ordered#LOWEST_PRECEDENCE}, which means
 * enrichers without explicit ordering run last.
 * </p>
 *
 * <h3>Implementation Guidelines:</h3>
 * <ul>
 *   <li>Enrichers should be stateless and thread-safe</li>
 *   <li>Enrichers should not throw exceptions; handle errors gracefully</li>
 *   <li>Enrichers should be idempotent where possible</li>
 *   <li>Enrichers should not modify the request body</li>
 *   <li>Enrichers must handle contexts where HTTP request is not available (e.g., stdio transport)</li>
 *   <li>Enrichers must define execution order via {@link #getOrder()}</li>
 * </ul>
 *
 * @see com.infobip.openapi.mcp.openapi.tool.ToolHandler
 * @see McpRequestContext
 * @see Ordered
 */
public interface ApiRequestEnricher extends Ordered {

    /**
     * Enriches the given request specification with additional headers, metadata, or other modifications.
     * <p>
     * This method is called before executing the downstream API call.
     * Implementations can modify the request by adding headers, cookies, or other request attributes
     * based on the MCP request context, which provides access to both HTTP servlet request and
     * MCP transport metadata (session ID, client info).
     * </p>
     * <p>
     * <strong>Important:</strong> Implementations must return the spec parameter (potentially modified)
     * and should NOT finalize the request by calling terminal operations like {@code retrieve()},
     * {@code exchange()}, or {@code toEntity()}. The caller is responsible for
     * executing the final request.
     * </p>
     *
     * @param spec the request specification to enrich; never null
     * @param context the MCP request context providing access to HTTP request and MCP metadata; never null
     * @return the enriched request specification (typically the same instance as spec parameter)
     */
    RestClient.RequestHeadersSpec<?> enrich(RestClient.RequestHeadersSpec<?> spec, McpRequestContext context);

    /**
     * Returns the name of this enricher.
     * <p>
     * This name is used for logging and debugging purposes to identify which enricher
     * is being applied or has failed during request enrichment.
     * </p>
     * <p>
     * The default implementation returns the simple class name, but implementations
     * can override this to provide a custom name if needed (e.g., for proxy beans
     * or when multiple instances of the same enricher class are used).
     * </p>
     *
     * @return the name of the enricher; defaults to the simple class name
     */
    default String name() {
        return this.getClass().getSimpleName();
    }

    /**
     * Returns the execution order of this enricher.
     * <p>
     * Enrichers with lower order values execute before enrichers with higher order values.
     * The default implementation returns {@link Ordered#LOWEST_PRECEDENCE}, which means
     * enrichers without explicit ordering will run last.
     * </p>
     * <p>
     * Implementations should define a public ORDER constant and return it from this method:
     * </p>
     * <pre>{@code
     * public class MyEnricher implements ApiRequestEnricher {
     *     public static final int ORDER = 150;
     *
     *     @Override
     *     public int getOrder() {
     *         return ORDER;
     *     }
     * }
     * }</pre>
     *
     * @return the order value (lower values execute first); defaults to {@link Ordered#LOWEST_PRECEDENCE}
     */
    @Override
    default int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
