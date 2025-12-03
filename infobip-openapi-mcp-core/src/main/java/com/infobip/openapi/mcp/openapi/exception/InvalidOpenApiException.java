package com.infobip.openapi.mcp.openapi.exception;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Exception thrown when an OpenAPI specification is invalid or cannot be processed.
 * <p>
 * This exception is typically thrown during OpenAPI parsing, validation, or reference resolution
 * phases when the specification contains errors, malformed content, or unresolvable references.
 * The exception provides detailed error messages to help identify and fix issues in the
 * OpenAPI specification.
 * <p>
 * The exception maintains a list of validation or parsing error messages that can be accessed
 * via {@link #getMessages()} for detailed diagnostics.
 */
@NullMarked
public final class InvalidOpenApiException extends RuntimeException {

    /**
     * List of detailed error messages associated with this exception.
     * This list is immutable and never null.
     */
    private final List<String> messages;

    /**
     * Private constructor for creating an exception with a message and optional error details.
     *
     * @param message the main error message describing the exception cause
     * @param messages optional list of detailed error messages; may be null
     */
    private InvalidOpenApiException(String message, @Nullable List<String> messages) {
        super(message);
        this.messages = (messages == null) ? List.of() : List.copyOf(messages);
    }

    /**
     * Private constructor for creating an exception with a message and underlying cause.
     *
     * @param message the main error message describing the exception cause
     * @param cause the underlying throwable that caused this exception
     */
    private InvalidOpenApiException(String message, Throwable cause) {
        super(message, cause);
        this.messages = List.of();
    }

    /**
     * Creates an {@code InvalidOpenApiException} for parsing errors.
     * <p>
     * This factory method should be used when OpenAPI specification parsing fails
     * due to syntax errors, invalid structure, or other parsing-related issues.
     *
     * @param uri the URI of the OpenAPI specification that failed to parse
     * @param messages optional list of specific parsing error messages; may be null
     * @return a new {@code InvalidOpenApiException} instance for parsing errors
     */
    public static InvalidOpenApiException becauseOfErrorsWhileParsing(URI uri, @Nullable List<String> messages) {
        return new InvalidOpenApiException(
                String.format(
                        "Invalid OpenAPI spec: %s."
                                + " Check the validity of the OpenAPI specification."
                                + " Explore the logs for the additional details.",
                        uri),
                messages);
    }

    /**
     * Creates an {@code InvalidOpenApiException} for reference resolution errors.
     * <p>
     * This factory method should be used when OpenAPI specification contains
     * unresolvable references (e.g., broken $ref links, missing components).
     *
     * @param messages optional list of specific reference resolution error messages; may be null
     * @return a new {@code InvalidOpenApiException} instance for reference resolution errors
     */
    public static InvalidOpenApiException becauseOfErrorsWhileResolvingReferences(@Nullable List<String> messages) {
        return new InvalidOpenApiException(
                "Invalid OpenAPI spec: errors occurred while resolving references."
                        + " Explore the logs for the additional details.",
                messages);
    }

    /**
     * Creates an {@code InvalidOpenApiException} for reference resolution errors with an underlying cause.
     * <p>
     * This factory method should be used when OpenAPI reference resolution fails
     * due to an underlying exception (e.g., network issues, file system errors).
     *
     * @param cause the underlying throwable that caused the reference resolution failure
     * @return a new {@code InvalidOpenApiException} instance for reference resolution errors
     */
    public static InvalidOpenApiException becauseOfErrorsWhileResolvingReferences(Throwable cause) {
        return new InvalidOpenApiException(
                "Invalid OpenAPI spec: errors occurred while resolving references."
                        + " Check the validity of the OpenAPI specification."
                        + " Explore the logs for the additional details.",
                cause);
    }

    /**
     * Returns the list of detailed error messages associated with this exception.
     * <p>
     * These messages provide specific details about what went wrong during
     * OpenAPI processing and can be used for detailed error reporting or debugging.
     *
     * @return an immutable list of error messages; never null but may be empty
     */
    public List<String> getMessages() {
        return messages;
    }
}
