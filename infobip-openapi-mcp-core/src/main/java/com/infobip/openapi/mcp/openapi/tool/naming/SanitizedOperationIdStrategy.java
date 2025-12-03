package com.infobip.openapi.mcp.openapi.tool.naming;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;

/**
 * Naming strategy that uses the operation ID from the OpenAPI specification with extensive sanitization.
 * <p>
 * This strategy takes the operationId and applies comprehensive sanitization rules to ensure
 * the resulting name follows consistent naming conventions suitable for MCP tool identifiers.
 * It's ideal when you want to use operationIds but need them to conform to specific formatting
 * requirements such as lowercase-only, underscore-separated naming conventions.
 * </p>
 *
 * <h3>Sanitization Process:</h3>
 * <p>The strategy applies the following transformations in order:</p>
 * <ol>
 *   <li><strong>Validation</strong> - Ensures operationId is not null or empty</li>
 *   <li><strong>Character Processing</strong> - Processes each character individually:
 *     <ul>
 *       <li>Alphanumeric characters → converted to lowercase and kept</li>
 *       <li>All other characters (including whitespace, punctuation) → replaced with underscores</li>
 *     </ul>
 *   </li>
 *   <li><strong>Underscore Normalization</strong> - Prevents consecutive underscores during processing</li>
 *   <li><strong>Trimming</strong> - Removes any trailing underscore</li>
 *   <li><strong>Final Validation</strong> - Ensures result contains at least one alphanumeric character</li>
 * </ol>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Converts all text to lowercase for consistency</li>
 *   <li>Replaces spaces, punctuation, and special characters with underscores</li>
 *   <li>Prevents leading underscores (processing only starts after first alphanumeric character)</li>
 *   <li>Prevents trailing underscores (automatically removed at the end)</li>
 *   <li>Prevents consecutive underscores (collapses multiple into single)</li>
 *   <li>Preserves existing underscores where appropriate</li>
 * </ul>
 *
 * <h3>Examples:</h3>
 * <ul>
 *   <li>{@code "getUserById"} → {@code "getuserbyid"}</li>
 *   <li>{@code "Create-User-Profile"} → {@code "create_user_profile"}</li>
 *   <li>{@code "Send SMS Message"} → {@code "send_sms_message"}</li>
 *   <li>{@code "___Update__User__Settings___"} → {@code "update_user_settings"}</li>
 *   <li>{@code "GetUser123Data"} → {@code "getuser123data"}</li>
 *   <li>{@code "send_sms_message"} → {@code "send_sms_message"} (already valid)</li>
 * </ul>
 *
 * <h3>Error Handling:</h3>
 * <p>This strategy throws {@link IllegalArgumentException} in the following cases:</p>
 * <ul>
 *   <li>When operationId is null</li>
 *   <li>When operationId contains no valid alphanumeric characters (e.g., only punctuation)</li>
 * </ul>
 *
 * <h3>When to Use:</h3>
 * <ul>
 *   <li>You want to use operationIds but need consistent formatting</li>
 *   <li>Your tool names must follow specific naming conventions (lowercase, underscores)</li>
 *   <li>You have operationIds with mixed casing, spaces, or special characters</li>
 *   <li>You need predictable, filesystem-safe tool names</li>
 * </ul>
 *
 * @see NamingStrategy
 * @see OperationIdStrategy
 * @see EndpointStrategy
 */
public class SanitizedOperationIdStrategy implements NamingStrategy {

    /**
     * Generates a sanitized tool name from the operation's operationId.
     * <p>
     * This method applies comprehensive sanitization to transform the operationId
     * into a consistent, lowercase, underscore-separated format suitable for use
     * as MCP tool identifiers. The sanitization ensures compatibility with various
     * systems while preserving the semantic meaning of the original operationId.
     * </p>
     *
     * @param operation the full OpenAPI operation containing the operationId to sanitize
     * @return the sanitized tool name in lowercase with underscores as separators
     * @throws IllegalArgumentException if operationId is null with message
     *         "Operation ID is null - cannot determine how to proceed with naming."
     * @throws IllegalArgumentException if operationId contains no valid alphanumeric characters with message
     *         "Operation ID 'xyz' contains no valid alphanumeric characters - cannot generate a valid name."
     */
    @Override
    public String name(FullOperation operation) {
        String operationId = operation.operation().getOperationId();

        if (operationId == null) {
            throw new IllegalArgumentException("Operation ID is null - cannot determine how to proceed with naming.");
        }

        StringBuilder result = new StringBuilder();
        boolean lastWasUnderscore = false;

        for (int i = 0; i < operationId.length(); i++) {
            char c = operationId.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                result.append(Character.toLowerCase(c));
                lastWasUnderscore = false;
            } else {
                if (!lastWasUnderscore && !result.isEmpty()) {
                    result.append('_');
                    lastWasUnderscore = true;
                }
            }
        }

        if (!result.isEmpty() && result.charAt(result.length() - 1) == '_') {
            result.setLength(result.length() - 1);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Operation ID '%s' contains no valid alphanumeric characters - cannot generate a valid name.",
                    operationId));
        }

        return result.toString();
    }
}
