package com.infobip.openapi.mcp.auth;

import com.infobip.openapi.mcp.McpRequestContext;
import java.util.Optional;

/**
 * Provides the credential value used to authorize requests.
 *
 * <p>Library consumers can implement this interface to supply credentials from any source —
 * HTTP headers, token vaults, secrets managers, environment variables, and so on. The default
 * implementation ({@link HttpServletRequestCredentialProvider}) reads the
 * {@code Authorization} header from the incoming HTTP request.
 *
 * <p>Both {@link com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter} and
 * {@link com.infobip.openapi.mcp.openapi.tool.ToolHandler} use the same provider instance,
 * ensuring the credential used for authentication validation and downstream API forwarding
 * is always identical.
 *
 * <p>Returns {@link Optional#empty()} when no credential is available — for example when
 * the header is absent or the configured source is unavailable.
 *
 * <p><strong>Error handling:</strong> Unchecked exceptions thrown by implementations are
 * caught by the framework. In {@link com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter}
 * an exception is treated as missing credentials and results in a {@code 401 Unauthorized}
 * response. In {@link com.infobip.openapi.mcp.openapi.tool.ToolHandler} an exception aborts
 * the downstream API call and returns an error tool result. In both cases the exception is
 * logged at {@code ERROR} level.
 */
public interface CredentialProvider {

    /**
     * Provides the credential value from the given request context.
     *
     * @param context the current MCP request context
     * @return the credential string (e.g. {@code "Bearer eyJ..."}) or empty if unavailable
     */
    Optional<String> provide(McpRequestContext context);
}
