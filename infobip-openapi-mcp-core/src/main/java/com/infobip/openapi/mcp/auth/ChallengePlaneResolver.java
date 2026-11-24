package com.infobip.openapi.mcp.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Chooses the {@link ChallengePlane} of a downstream API authorization failure per request in
 * {@link AuthProperties.AuthMode#PARTIAL_AUTH_AUTODETECT} mode: the configured
 * {@link AuthProperties.ChallengeHint} rules first, then built-in patterns for well-known MCP hosts, and
 * the configured default plane for everything else. A user-experience router, not a security control —
 * the backing API remains the authorization authority.
 */
public class ChallengePlaneResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChallengePlaneResolver.class);

    /** Built-in patterns for MCP hosts observed in production; user-configured hints take precedence. */
    private static final List<CompiledHint> KNOWN_CLIENTS = List.of(
            new CompiledHint(compile("known client", "claude", "user-agent"), null, ChallengePlane.HTTP),
            new CompiledHint(compile("known client", "openai-mcp", "user-agent"), null, ChallengePlane.IN_META),
            new CompiledHint(compile("known client", "codex-mcp-client", "user-agent"), null, ChallengePlane.IN_META));

    private final List<CompiledHint> hints;
    private final ChallengePlane defaultChallengePlane;

    public ChallengePlaneResolver(
            List<AuthProperties.ChallengeHint> clientHints, ChallengePlane defaultChallengePlane) {
        this.hints = new ArrayList<>(clientHints.size() + KNOWN_CLIENTS.size());
        clientHints.stream().map(ChallengePlaneResolver::compile).forEach(this.hints::add);
        this.hints.addAll(KNOWN_CLIENTS);
        this.defaultChallengePlane = defaultChallengePlane;
        LOGGER.debug(
                "Challenge plane resolver for PARTIAL_AUTH_AUTODETECT: {} configured hints, {} known clients, default {}",
                clientHints.size(),
                KNOWN_CLIENTS.size(),
                defaultChallengePlane);
    }

    /**
     * Resolves the plane for a single request; both identity sources are optional (no request on stdio, no
     * client name on transports without a handshake).
     */
    public ChallengePlane resolve(@Nullable HttpServletRequest request, @Nullable String clientName) {
        var userAgent = request != null ? request.getHeader(HttpHeaders.USER_AGENT) : null;
        return hints.stream()
                .filter(hint -> hint.matches(userAgent, clientName))
                .map(CompiledHint::plane)
                .findFirst()
                .orElse(defaultChallengePlane);
    }

    private static CompiledHint compile(AuthProperties.ChallengeHint hint) {
        return new CompiledHint(
                compile("client-hint", hint.userAgentPattern(), "user-agent-pattern"),
                compile("client-hint", hint.clientNamePattern(), "client-name-pattern"),
                hint.plane());
    }

    private static @Nullable Pattern compile(String source, @Nullable String regex, String property) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid regular expression in %s %s: '%s'".formatted(source, property, regex), exception);
        }
    }

    private record CompiledHint(
            @Nullable Pattern userAgent, @Nullable Pattern clientName, ChallengePlane plane) {

        private boolean matches(@Nullable String userAgent, @Nullable String clientName) {
            // A rule matches when each pattern it configures matches; configuring none matches nothing.
            if (this.userAgent == null && this.clientName == null) {
                return false;
            }
            return matches(this.userAgent, userAgent) && matches(this.clientName, clientName);
        }

        private static boolean matches(@Nullable Pattern pattern, @Nullable String value) {
            return pattern == null || (value != null && pattern.matcher(value).find());
        }
    }
}
