package com.infobip.openapi.mcp.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Server-internal request-attribute marker that carries a downstream API {@code 401}/{@code 403} from
 * {@link com.infobip.openapi.mcp.openapi.tool.ToolHandler} to
 * {@link com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter}, which remaps it into an HTTP
 * authentication challenge. Never part of the wire payload.
 */
public final class ApiAuthFailure {

    public static final String REQUEST_ATTRIBUTE = ApiAuthFailure.class.getName() + ".status";

    private ApiAuthFailure() {}

    /** Marks the request when {@code status} is {@code 401} or {@code 403}; other statuses are ignored. */
    public static void mark(HttpServletRequest request, @Nullable HttpStatusCode status) {
        if (isAuthorizationFailure(status)) {
            request.setAttribute(REQUEST_ATTRIBUTE, status);
        }
    }

    /** The authorization failure this request was marked with, or {@code null}. */
    public static @Nullable HttpStatusCode read(HttpServletRequest request) {
        return request.getAttribute(REQUEST_ATTRIBUTE) instanceof HttpStatusCode status ? status : null;
    }

    /** Whether {@code status} is an authorization failure ({@code 401} or {@code 403}). */
    public static boolean isAuthorizationFailure(@Nullable HttpStatusCode status) {
        return status != null
                && (status.value() == HttpStatus.UNAUTHORIZED.value()
                        || status.value() == HttpStatus.FORBIDDEN.value());
    }
}
