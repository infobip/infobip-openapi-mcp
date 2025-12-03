package com.infobip.openapi.mcp.openapi.tool.naming;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;

/**
 * Naming strategy that constructs tool names from HTTP method and path information.
 * <p>
 * This strategy generates names by combining the HTTP method with the path, converting
 * the result to a standardized format suitable for MCP tool identifiers.
 * </p>
 *
 * <h3>Naming Rules:</h3>
 * <ul>
 *   <li>HTTP method is converted to lowercase and used as prefix</li>
 *   <li>Path separators (/) are replaced with underscores, except leading slash which is skipped</li>
 *   <li>Hyphens (-) are replaced with underscores</li>
 *   <li>All characters are converted to lowercase</li>
 *   <li>Only alphanumeric characters and underscores are kept</li>
 *   <li>Other special characters are skipped</li>
 * </ul>
 *
 * <h3>Examples:</h3>
 * <ul>
 *   <li>{@code POST /sms/3/messages} → {@code post_sms_3_messages}</li>
 *   <li>{@code GET /users/{id}/profile} → {@code get_users_id_profile}</li>
 *   <li>{@code DELETE /api/v1/user-accounts} → {@code delete_api_v1_user_accounts}</li>
 * </ul>
 *
 * <p>
 * This strategy is particularly useful when operation IDs are not available or when
 * you want consistent naming based on the actual API endpoints.
 * </p>
 *
 * @see NamingStrategy
 * @see OperationIdStrategy
 * @see SanitizedOperationIdStrategy
 */
public class EndpointStrategy implements NamingStrategy {

    /**
     * Generates a tool name by combining HTTP method and path.
     * <p>
     * The method combines the HTTP method (in lowercase) with the sanitized path
     * to create a consistent, readable tool name that reflects the actual endpoint.
     * </p>
     *
     * @param operation the full OpenAPI operation containing HTTP method and path
     * @return the generated tool name in format: {method}_{sanitized_path}
     */
    @Override
    public String name(FullOperation operation) {
        var method = operation.method().name().toLowerCase();
        var path = operation.path();

        var result = new StringBuilder(method.length() + path.length() + 1);
        result.append(method).append('_');

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);

            if (c == '/') {
                // Skip leading slash, replace others with underscore
                if (i > 0) {
                    result.append('_');
                }
            } else if (c == '-') {
                // Replace hyphens with underscores
                result.append('_');
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                // Keep alphanumeric and underscores, convert to lowercase
                result.append(Character.toLowerCase(c));
            }
            // Skip any other special characters
        }

        return result.toString();
    }
}
