package com.infobip.openapi.mcp.openapi.exception;

public final class OpenApiFilterException extends RuntimeException {

    private OpenApiFilterException(String message, Throwable cause) {
        super(message, cause);
    }

    public static OpenApiFilterException becauseOfErrorsWhileFiltering(String filterName, Throwable cause) {
        return new OpenApiFilterException(
                String.format(
                        "OpenAPI spec filtering failed by filter: %s."
                                + " Check the validity of the OpenAPI specification."
                                + " Explore the logs for the additional details."
                                + " Disable te filter if needed.",
                        filterName),
                cause);
    }
}
