/**
 * HTTP request enrichers for downstream API calls.
 * <p>
 * This package provides an extensible enricher pattern for augmenting outgoing HTTP requests
 * with additional headers, context, or metadata before they're sent to downstream APIs.
 * Enrichers enable cross-cutting concerns like IP tracking and telemetry collection to be
 * applied consistently across all API calls.
 * </p>
 * <p>
 * <b>Important:</b> Enrichers are designed for <b>observability and non-critical metadata only</b>.
 * Critical security concerns like authorization are intentionally handled explicitly outside
 * the enricher chain to ensure visibility, auditability, and fail-fast behavior.
 * </p>
 *
 * <h2>Pattern Overview</h2>
 * <p>
 * Enrichers implement the {@link com.infobip.openapi.mcp.enricher.ApiRequestEnricher} interface,
 * which defines a single method for modifying request specifications. All registered enrichers
 * are automatically discovered by Spring's dependency injection and applied sequentially to
 * outgoing requests through a chain of responsibility pattern.
 * </p>
 *
 * <p>
 * Each enricher receives:
 * </p>
 * <ul>
 *   <li>The current {@link org.springframework.web.client.RestClient.RequestHeadersSpec} being built</li>
 *   <li>The {@link com.infobip.openapi.mcp.McpRequestContext} containing HTTP request and MCP metadata</li>
 * </ul>
 *
 * <p>
 * The enricher performs its augmentation and returns the modified specification, which is then
 * passed to the next enricher in the chain.
 * </p>
 *
 * <h2>Execution Flow</h2>
 * <p>
 * When an outgoing HTTP request is prepared:
 * </p>
 * <ol>
 *   <li>Base request specification is created with path, method, parameters, and body</li>
 *   <li>All registered enrichers are applied sequentially in the order determined by Spring</li>
 *   <li>Each enricher modifies the specification (adds headers, sets cookies, etc.)</li>
 *   <li>The final enriched specification is executed to make the actual HTTP call</li>
 * </ol>
 *
 * <h2>Error Handling</h2>
 * <p>
 * The enricher chain implements graceful degradation. If an enricher throws an exception:
 * </p>
 * <ul>
 *   <li>The exception is logged with full details including the enricher class name</li>
 *   <li>The request continues with the remaining enrichers using the current specification</li>
 *   <li>The HTTP call proceeds even if some enrichments fail</li>
 * </ul>
 *
 * <p>
 * This ensures that transient failures in enrichers (e.g., JSON serialization errors,
 * temporary context unavailability) don't prevent the core API functionality from working.
 * </p>
 *
 * <h2>Adding Custom Enrichers</h2>
 * <p>
 * To add a custom enricher to the system:
 * </p>
 *
 * <h3>Step 1: Implement the Interface</h3>
 * <pre>{@code
 * public class CustomHeaderEnricher implements ApiRequestEnricher {
 *     @Override
 *     public RestClient.RequestHeadersSpec<?> enrich(
 *             RestClient.RequestHeadersSpec<?> spec,
 *             McpRequestContext context
 *     ) {
 *         spec.header("X-Custom-Header", "custom-value");
 *         spec.header("X-Request-Id", UUID.randomUUID().toString());
 *         return spec;
 *     }
 * }
 * }</pre>
 *
 * <h3>Step 2: Define Execution Order</h3>
 * <p>
 * Define a public ORDER constant and implement {@code getOrder()} to control when your enricher
 * runs relative to others. Lower values execute first. The {@link com.infobip.openapi.mcp.enricher.ApiRequestEnricher} interface
 * extends {@link org.springframework.core.Ordered} which makes ordering mandatory.
 * </p>
 * <pre>{@code
 * public class CustomHeaderEnricher implements ApiRequestEnricher {
 *     public static final int ORDER = 350; // Between XForwardedHost (200) and McpClientInfo (300)
 *
 *     @Override
 *     public int getOrder() {
 *         return ORDER;
 *     }
 *
 *     @Override
 *     public RestClient.RequestHeadersSpec<?> enrich(...) { ... }
 * }
 * }</pre>
 *
 * <h3>Step 3: Register as Spring Bean</h3>
 * <p>
 * Add the enricher as a Spring bean in your configuration class. Spring will automatically
 * discover it and include it in the enricher chain in the order specified by {@code getOrder()}.
 * </p>
 * <pre>{@code
 * @Configuration
 * public class MyConfiguration {
 *     @Bean
 *     public ApiRequestEnricher customHeaderEnricher() {
 *         return new CustomHeaderEnricher();
 *     }
 * }
 * }</pre>
 *
 * <h3>Step 4: That's It!</h3>
 * <p>
 * Your enricher will be automatically applied to all outgoing HTTP requests in the specified order.
 * No further configuration or registration is needed.
 * </p>
 *
 * <h2>Enricher Execution Order</h2>
 * <p>
 * Enrichers are executed in order based on their {@code getOrder()} return value (lower values first).
 * The {@link com.infobip.openapi.mcp.enricher.ApiRequestEnricher} interface extends {@link org.springframework.core.Ordered}, making
 * ordering explicit and mandatory. The current execution order is:
 * </p>
 * <ol>
 *   <li>{@link com.infobip.openapi.mcp.enricher.XForwardedForEnricher} (100) - Establishes X-Forwarded-For chain</li>
 *   <li>{@link com.infobip.openapi.mcp.enricher.XForwardedHostEnricher} (200) - Adds X-Forwarded-Host header</li>
 *   <li>{@link com.infobip.openapi.mcp.enricher.UserAgentEnricher} (1000) - Sets User-Agent (runs last to avoid override)</li>
 * </ol>
 * <p>
 * When adding new enrichers, choose order values between existing enrichers (e.g., 150, 250, 350)
 * to allow future insertions.
 * </p>
 *
 * <h2>What Should NOT Be an Enricher</h2>
 * <p>
 * <b>Authorization and Authentication:</b> These are intentionally <b>not</b> implemented as enrichers.
 * Critical security concerns must be handled explicitly in code to ensure:
 * </p>
 * <ul>
 *   <li><b>Visibility:</b> Security logic must be obvious and easy to find during code reviews</li>
 *   <li><b>Auditability:</b> Authorization handling should not be hidden in a chain of enrichers</li>
 *   <li><b>Fail-fast behavior:</b> Auth failures must stop execution immediately, not degrade gracefully</li>
 *   <li><b>Security review compliance:</b> Auditors need to easily locate and review auth handling</li>
 * </ul>
 *
 * <p>
 * See {@link com.infobip.openapi.mcp.openapi.tool.ToolHandler} and
 * {@link com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter} for examples of
 * explicit authorization header forwarding that occurs <b>before</b> enrichers are applied.
 * </p>
 *
 * <h2>Provided Enrichers</h2>
 * <p>
 * This package includes several enrichers that are registered by default (in execution order):
 * </p>
 * <ul>
 *   <li>{@link com.infobip.openapi.mcp.enricher.XForwardedForEnricher} - Tracks original client IP addresses</li>
 *   <li>{@link com.infobip.openapi.mcp.enricher.XForwardedHostEnricher} - Preserves original host information</li>
 *   <li>{@link com.infobip.openapi.mcp.enricher.UserAgentEnricher} - Sets User-Agent header</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li><b>Stateless:</b> Enrichers should be stateless and thread-safe</li>
 *   <li><b>Defensive:</b> Check for null values in context before using them</li>
 *   <li><b>Fast:</b> Avoid expensive operations; enrichers are called for every request</li>
 *   <li><b>Logged:</b> Log at DEBUG level for normal enrichment, ERROR for failures</li>
 *   <li><b>Documented:</b> Include comprehensive JavaDoc explaining what your enricher does</li>
 *   <li><b>Ordered:</b> Implement {@code getOrder()} and define ORDER constant to control execution sequence</li>
 * </ul>
 *
 * @see com.infobip.openapi.mcp.enricher.ApiRequestEnricher
 * @see com.infobip.openapi.mcp.McpRequestContext
 */
@NullMarked
package com.infobip.openapi.mcp.enricher;

import org.jspecify.annotations.NullMarked;
