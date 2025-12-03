package com.infobip.openapi.mcp.openapi.schema;

import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

@NullMarked
public record DecomposedRequestData(
        ParametersByType parametersByType, @Nullable Body requestBody) {

    public static final MediaType SUPPORTED_MEDIA_TYPE = MediaType.APPLICATION_JSON;

    public static DecomposedRequestData empty() {
        return new DecomposedRequestData(ParametersByType.empty(), null);
    }

    public static DecomposedRequestData withParameters(ParametersByType parametersByType) {
        return new DecomposedRequestData(parametersByType, null);
    }

    public static DecomposedRequestData withParametersAndBodyContent(
            ParametersByType parametersByType, @Nullable Object requestBodyContent) {
        if (requestBodyContent == null) {
            return DecomposedRequestData.withParameters(parametersByType);
        }
        return new DecomposedRequestData(parametersByType, Body.inDefaultSupportedMediaType(requestBodyContent));
    }

    public static DecomposedRequestData withRequestBody(Object requestBodyContent) {
        return new DecomposedRequestData(
                ParametersByType.empty(), Body.inDefaultSupportedMediaType(requestBodyContent));
    }

    public record ParametersByType(
            Map<String, Object> path,
            Map<String, Object> query,
            Map<String, Object> header,
            Map<String, Object> cookie) {
        public static final String PATH = "path";
        public static final String QUERY = "query";
        public static final String HEADER = "header";
        public static final String COOKIE = "cookie";

        public static ParametersByType empty() {
            return new ParametersByType(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record Body(MediaType targetContentType, Object content) {

        public static Body inDefaultSupportedMediaType(Object content) {
            return new Body(SUPPORTED_MEDIA_TYPE, content);
        }
    }

    public Optional<Body> resolveRequestBody() {
        return Optional.ofNullable(requestBody);
    }
}
