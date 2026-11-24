package com.infobip.openapi.mcp.auth;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.AuthProperties.AuthMode;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;

/**
 * Applies partial-authentication handling to downstream API authorization failures: on the
 * {@link ChallengePlane#HTTP} plane the request is marked for remapping by
 * {@link com.infobip.openapi.mcp.auth.web.InitialAuthenticationFilter}; on the
 * {@link ChallengePlane#IN_META} plane the tool result carries the challenge in
 * {@code _meta["mcp/www_authenticate"]}.
 */
public class PartialAuthChallengeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartialAuthChallengeHandler.class);

    private static final String WWW_AUTHENTICATE_META_KEY = "mcp/www_authenticate";

    private final AuthMode mode;
    private final Optional<ChallengePlaneResolver> challengePlaneResolver;
    private final Optional<WwwAuthenticateProvider> wwwAuthenticateProvider;
    private final boolean httpChallengePlaneAvailable;

    public PartialAuthChallengeHandler(
            AuthMode mode,
            Optional<ChallengePlaneResolver> challengePlaneResolver,
            Optional<WwwAuthenticateProvider> wwwAuthenticateProvider,
            boolean httpChallengePlaneAvailable) {
        this.mode = mode;
        this.challengePlaneResolver = challengePlaneResolver;
        this.wwwAuthenticateProvider = wwwAuthenticateProvider;
        this.httpChallengePlaneAvailable = httpChallengePlaneAvailable;
        if (mode == AuthMode.PARTIAL_AUTH_OPENAI_COMPATIBLE && wwwAuthenticateProvider.isEmpty()) {
            LOGGER.warn("API auth error annotation is enabled but no WwwAuthenticateProvider is available;"
                    + " tool results will not carry the in-band authentication challenge."
                    + " Check the security.auth.oauth configuration.");
        }
    }

    /**
     * Handles an API authorization failure and returns the tool result to deliver: marking the request for
     * HTTP remapping on the HTTP plane, annotating it in-band on the in-meta plane when a
     * {@link WwwAuthenticateProvider} is configured, and otherwise applying {@code plainError} to the API
     * error body. Non-authorization statuses and requests without a servlet request (e.g. stdio) always
     * take the plain path; synthetic framework statuses must not mark, as they would trigger a client login
     * flow that cannot succeed.
     */
    public McpSchema.CallToolResult handle(
            McpRequestContext context,
            HttpStatusCode statusCode,
            String apiErrorBody,
            Function<String, McpSchema.CallToolResult> plainError) {
        var servletRequest = context.httpServletRequest();
        if (servletRequest == null || !ApiAuthFailure.isAuthorizationFailure(statusCode)) {
            return plainError.apply(apiErrorBody);
        }
        if (effectivePlane(context, servletRequest) == ChallengePlane.HTTP) {
            ApiAuthFailure.mark(servletRequest, statusCode);
            return plainError.apply(apiErrorBody);
        }
        return wwwAuthenticateProvider
                .map(provider -> annotatedResult(servletRequest, apiErrorBody, provider))
                .orElseGet(() -> plainError.apply(apiErrorBody));
    }

    private ChallengePlane effectivePlane(McpRequestContext context, HttpServletRequest servletRequest) {
        return switch (mode) {
            case PARTIAL_AUTH_CLAUDE_COMPATIBLE -> ChallengePlane.HTTP;
            case PARTIAL_AUTH_OPENAI_COMPATIBLE -> ChallengePlane.IN_META;
            case PARTIAL_AUTH_AUTODETECT -> resolvePlane(servletRequest, context);
            case ALL_OR_NOTHING -> throw new IllegalStateException("No partial-auth handling in ALL_OR_NOTHING mode");
        };
    }

    private McpSchema.CallToolResult annotatedResult(
            HttpServletRequest servletRequest, String apiErrorBody, WwwAuthenticateProvider provider) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(apiErrorBody)))
                .isError(true)
                .meta(Map.of(
                        WWW_AUTHENTICATE_META_KEY, List.of(provider.buildWwwAuthenticateChallenge(servletRequest))))
                .build();
    }

    /**
     * The effective plane of the in-flight request: what the client hints say, downgraded to
     * {@link ChallengePlane#IN_META} whenever the transport cannot write an HTTP challenge anymore.
     */
    private ChallengePlane resolvePlane(HttpServletRequest servletRequest, McpRequestContext context) {
        var plane = challengePlaneResolver
                .orElseThrow(() -> new IllegalStateException("No challenge plane resolver configured"))
                .resolve(servletRequest, clientName(context));
        return plane == ChallengePlane.HTTP && httpChallengePlaneAvailable
                ? ChallengePlane.HTTP
                : ChallengePlane.IN_META;
    }

    private @Nullable String clientName(McpRequestContext context) {
        var clientInfo = context.clientInfo();
        return clientInfo != null ? clientInfo.name() : null;
    }
}
