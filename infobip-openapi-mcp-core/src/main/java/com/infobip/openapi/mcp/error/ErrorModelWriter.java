package com.infobip.openapi.mcp.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;

public class ErrorModelWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorModelWriter.class);

    private final ObjectMapper objectMapper;
    private final ErrorModelProvider<?> errorModelProvider;

    public ErrorModelWriter(ObjectMapper objectMapper, ErrorModelProvider<?> errorModelProvider) {
        this.objectMapper = objectMapper;
        this.errorModelProvider = errorModelProvider;
    }

    public @NonNull String writeErrorModelAsJson(
            @NonNull HttpStatusCode statusCode, HttpServletRequest request, Throwable cause) {
        var errorModel = errorModelProvider.provide(statusCode, request, cause);
        try {
            return objectMapper.writeValueAsString(errorModel);
        } catch (Exception e) {
            LOGGER.error("Error serializing error model. The default error response will be used.", e);
            return DefaultErrorModelProvider.INTERNAL_SERVER_ERROR_JSON_REPRESENTATION;
        }
    }

    public @NonNull String writeErrorModelAsJson(@NonNull HttpStatusCode statusCode) {
        return writeErrorModelAsJson(statusCode, null, null);
    }
}
