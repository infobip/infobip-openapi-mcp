package com.infobip.openapi.mcp.openapi.tool.naming;

/**
 * Enumeration of available tool naming strategies for OpenAPI operations.
 * <p>
 * This enum defines the different approaches available for generating MCP tool names
 * from OpenAPI operations. Each strategy type corresponds to a specific implementation
 * of the {@link NamingStrategy} interface with its own naming rules and behavior.
 * </p>
 *
 * <h3>Strategy Comparison:</h3>
 * <table border="1">
 *   <tr>
 *     <th>Strategy</th>
 *     <th>Source</th>
 *     <th>Sanitization</th>
 *     <th>Error Handling</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #OPERATION_ID}</td>
 *     <td>operationId field</td>
 *     <td>None (as-is)</td>
 *     <td>Exception on null/empty</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #SANITIZED_OPERATION_ID}</td>
 *     <td>operationId field</td>
 *     <td>Lowercase + underscore replacement</td>
 *     <td>Exception on null/empty/invalid</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #ENDPOINT}</td>
 *     <td>HTTP method + path</td>
 *     <td>Lowercase + character replacement</td>
 *     <td>Always succeeds</td>
 *   </tr>
 * </table>
 *
 * @see NamingStrategy
 * @see NamingStrategyFactory
 */
public enum NamingStrategyType {

    /**
     * Uses the operationId from the OpenAPI specification as-is.
     * <p>
     * This strategy returns the operationId exactly as specified in the OpenAPI
     * document without any modifications. It preserves the original casing and
     * special characters, making it suitable when you want to maintain exact
     * consistency with the OpenAPI documentation.
     * </p>
     *
     * <h4>Behavior:</h4>
     * <ul>
     *   <li>Returns operationId without any sanitization</li>
     *   <li>Throws {@link IllegalArgumentException} if operationId is null or empty</li>
     *   <li>Preserves original casing and special characters</li>
     * </ul>
     *
     * <h4>Example:</h4>
     * {@code "GetUserProfile"} → {@code "GetUserProfile"}
     *
     * @see OperationIdStrategy
     */
    OPERATION_ID,

    /**
     * Uses the operationId from the OpenAPI specification with extensive sanitization.
     * <p>
     * This strategy takes the operationId and applies comprehensive sanitization
     * to ensure the resulting name follows consistent naming conventions and is
     * suitable for use as an MCP tool identifier.
     * </p>
     *
     * <h4>Sanitization Rules:</h4>
     * <ul>
     *   <li>Converts all characters to lowercase</li>
     *   <li>Replaces all non-alphanumeric characters (including whitespace) with underscores</li>
     *   <li>Removes leading and trailing underscores</li>
     *   <li>Collapses consecutive underscores into single underscores</li>
     *   <li>Throws exception if no valid alphanumeric characters remain</li>
     * </ul>
     *
     * <h4>Examples:</h4>
     * <ul>
     *   <li>{@code "GetUserProfile"} → {@code "getuserprofile"}</li>
     *   <li>{@code "Create-User Profile"} → {@code "create_user_profile"}</li>
     *   <li>{@code "__Update__User__"} → {@code "update_user"}</li>
     * </ul>
     *
     * @see SanitizedOperationIdStrategy
     */
    SANITIZED_OPERATION_ID,

    /**
     * Constructs name from HTTP method and path.
     * <p>
     * This strategy generates tool names by combining the HTTP method with the
     * endpoint path, applying sanitization to create consistent, readable names
     * that reflect the actual API endpoints. This is particularly useful when
     * operationId is not available or when you prefer endpoint-based naming.
     * </p>
     *
     * <h4>Naming Format:</h4>
     * {@code {method}_{sanitized_path}}
     *
     * <h4>Path Sanitization Rules:</h4>
     * <ul>
     *   <li>HTTP method converted to lowercase</li>
     *   <li>Path separators (/) replaced with underscores (except leading slash)</li>
     *   <li>Hyphens (-) replaced with underscores</li>
     *   <li>All characters converted to lowercase</li>
     *   <li>Only alphanumeric characters and underscores kept</li>
     * </ul>
     *
     * <h4>Examples:</h4>
     * <ul>
     *   <li>{@code POST /sms/3/messages} → {@code "post_sms_3_messages"}</li>
     *   <li>{@code GET /users/{id}/profile} → {@code "get_users_id_profile"}</li>
     *   <li>{@code DELETE /api/v1/user-accounts} → {@code "delete_api_v1_user_accounts"}</li>
     * </ul>
     *
     * @see EndpointStrategy
     */
    ENDPOINT
}
