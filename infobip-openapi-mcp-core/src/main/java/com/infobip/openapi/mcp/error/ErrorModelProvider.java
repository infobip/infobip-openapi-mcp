package com.infobip.openapi.mcp.error;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatusCode;

public interface ErrorModelProvider<T> {

    @NonNull
    T provide(@NonNull HttpStatusCode responseStatusCode, HttpServletRequest request, Throwable cause);
}
