package com.infobip.openapi.mcp.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Default implementation of ErrorModelProvider that provides standard error models
 * for common HTTP error responses.
 */
public class DefaultErrorModelProvider implements ErrorModelProvider<DefaultErrorModel> {

    private static final DefaultErrorModel INTERNAL_SERVER_ERROR =
            new DefaultErrorModel("Internal Server Error", "An unexpected error occurred on the server.");

    private static final Map<Integer, DefaultErrorModel> DEFAULT_RESPONSE_MAPPING = Map.of(
            HttpStatus.BAD_REQUEST.value(),
                    new DefaultErrorModel("Bad Request", "Check the request syntax and parameters and try again."),
            HttpStatus.UNAUTHORIZED.value(),
                    new DefaultErrorModel("Unauthorized", "Authentication required. Please provide valid credentials."),
            HttpStatus.FORBIDDEN.value(),
                    new DefaultErrorModel(
                            "Forbidden", "Access denied. You don't have permission to access this resource."),
            HttpStatus.NOT_FOUND.value(), new DefaultErrorModel("Not Found", "The requested resource was not found."),
            HttpStatus.TOO_MANY_REQUESTS.value(),
                    new DefaultErrorModel("Too Many Requests", "Request limit exceeded. Please try again later."),
            HttpStatus.BAD_GATEWAY.value(),
                    new DefaultErrorModel(
                            "Bad Gateway", "The server received an invalid response from an upstream server."),
            HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_SERVER_ERROR);

    public static final String INTERNAL_SERVER_ERROR_JSON_REPRESENTATION = "{\"error\":\"%s\",\"description\":\"%s\"}"
            .formatted(INTERNAL_SERVER_ERROR.error(), INTERNAL_SERVER_ERROR.description());

    @Override
    public @NonNull DefaultErrorModel provide(
            @NonNull HttpStatusCode responseStatusCode, HttpServletRequest request, Throwable cause) {
        var mappedDefaultModel = DEFAULT_RESPONSE_MAPPING.get(responseStatusCode.value());
        if (mappedDefaultModel != null) {
            return mappedDefaultModel;
        }

        var httpStatus = HttpStatus.valueOf(responseStatusCode.value());

        if (responseStatusCode.is4xxClientError()) {
            return new DefaultErrorModel(
                    httpStatus.getReasonPhrase(), "A client error occurred. Please check your request.");
        } else if (responseStatusCode.is5xxServerError()) {
            return new DefaultErrorModel(
                    httpStatus.getReasonPhrase(), "An unexpected server error occurred. Please try again later.");
        } else {
            return new DefaultErrorModel("Unexpected Server Response", "Please try again.");
        }
    }
}
