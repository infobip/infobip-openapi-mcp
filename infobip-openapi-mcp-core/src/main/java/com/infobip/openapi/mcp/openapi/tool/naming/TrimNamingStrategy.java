package com.infobip.openapi.mcp.openapi.tool.naming;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;

/**
 * A decorator naming strategy that trims tool names to a maximum length.
 * <p>
 * This strategy implements the Decorator pattern to wrap any existing {@link NamingStrategy}
 * and ensure that the generated tool names do not exceed a specified maximum length.
 * If the delegate strategy returns a name longer than the maximum length, it will be
 * truncated from the end to fit within the specified limit.
 * </p>
 *
 * <h3>Use Cases:</h3>
 * <ul>
 *   <li>Integrating with systems that have strict identifier length limitations</li>
 *   <li>Ensuring consistent, shorter tool names for better readability</li>
 *   <li>Working within constraints of specific protocols or storage systems</li>
 *   <li>Creating uniform naming where very long operation IDs or paths exist</li>
 * </ul>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li><strong>Delegation</strong> - Calls the wrapped strategy to generate the initial name</li>
 *   <li><strong>Length Check</strong> - If the name is within limits, returns it unchanged</li>
 *   <li><strong>Truncation</strong> - If the name exceeds the limit, truncates from the end</li>
 * </ul>
 *
 * <h3>Examples:</h3>
 * <p>With maxLength = 10:</p>
 * <ul>
 *   <li>{@code "getUserById"} → {@code "getUserById"} (11 chars, fits)</li>
 *   <li>{@code "createUserProfileWithSettings"} → {@code "createUser"} (truncated to 10 chars)</li>
 *   <li>{@code "get_sms_message_status_by_id"} → {@code "get_sms_me"} (truncated to 10 chars)</li>
 * </ul>
 *
 * <h3>Configuration:</h3>
 * <p>
 * This strategy is automatically applied by the {@link NamingStrategyFactory} when
 * a maxLength is specified in the application configuration. It can wrap any of the
 * base naming strategies (OperationId, SanitizedOperationId, or Endpoint).
 * </p>
 *
 * <h3>Important Notes:</h3>
 * <ul>
 *   <li>Truncation may result in non-unique names if multiple operations have similar prefixes</li>
 *   <li>Consider the maxLength carefully to balance brevity with uniqueness</li>
 *   <li>The delegate strategy's exceptions are passed through unchanged</li>
 * </ul>
 *
 * @see NamingStrategy
 * @see NamingStrategyFactory
 * @see OperationIdStrategy
 * @see SanitizedOperationIdStrategy
 * @see EndpointStrategy
 */
public class TrimNamingStrategy implements NamingStrategy {

    private final NamingStrategy delegate;
    private final int maxLength;

    /**
     * Creates a new trimming naming strategy that wraps the given delegate.
     * <p>
     * The created strategy will delegate name generation to the provided strategy
     * and then ensure the result does not exceed the specified maximum length.
     * </p>
     *
     * @param delegate the naming strategy to wrap and delegate name generation to
     * @param maxLength the maximum allowed length for generated names, must be positive
     * @throws IllegalArgumentException if maxLength is not positive (≤ 0)
     */
    public TrimNamingStrategy(NamingStrategy delegate, int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("Max length must be positive, but was: " + maxLength);
        }
        this.delegate = delegate;
        this.maxLength = maxLength;
    }

    /**
     * Generates a tool name for the given operation, ensuring it does not exceed the maximum length.
     * <p>
     * The name generation process follows these steps:
     * </p>
     * <ol>
     *   <li>Delegates to the wrapped strategy to generate the initial name</li>
     *   <li>If the delegate returns {@code null}, passes it through unchanged</li>
     *   <li>If the generated name length is within the limit, returns it unchanged</li>
     *   <li>If the name exceeds the limit, truncates it to exactly the maximum length</li>
     * </ol>
     *
     * <p>
     * <strong>Note:</strong> Truncation is performed by taking the first {@code maxLength}
     * characters of the string. This may result in incomplete words or semantically
     * unclear names if the limit is too restrictive.
     * </p>
     *
     * @param operation the full operation information to generate a name for
     * @return the generated tool name, trimmed to the maximum length if necessary,
     *         or {@code null} if the delegate returns {@code null}
     * @throws IllegalArgumentException if the delegate strategy throws this exception
     *         during name generation (e.g., for invalid operation data)
     */
    @Override
    public String name(FullOperation operation) {
        var name = delegate.name(operation);

        if (name.length() <= maxLength) {
            return name;
        }

        return name.substring(0, maxLength);
    }
}
