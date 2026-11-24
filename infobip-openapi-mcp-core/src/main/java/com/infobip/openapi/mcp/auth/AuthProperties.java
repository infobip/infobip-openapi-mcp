package com.infobip.openapi.mcp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Authentication configuration properties for OpenAPI MCP Server.
 *
 * @param enabled                  Enable API authentication. Default is false.
 * @param authUrl                  The API endpoint URL to validate credentials against. Required only in
 *                                 {@link AuthMode#ALL_OR_NOTHING}.
 * @param connectTimeout           Connection timeout for the validation API call. Default is 5 seconds.
 * @param readTimeout              Read timeout for the validation API call. Default is 5 seconds.
 * @param overrideExternalResponse Whether to override the external response with the internal one.
 *                                 This is useful for cases where you want to control the response format
 *                                 or content returned to the client. If set to true, the response will
 *                                 be overridden by the model provided by {@link com.infobip.openapi.mcp.error.ErrorModelProvider}.
 *                                 Default is false.
 * @param mode                     Authentication mode: where authorization is decided and on which plane
 *                                 failures are delivered.
 * @param clientHints              Ordered rules assigning a {@link ChallengePlane} per request in
 *                                 {@link AuthMode#PARTIAL_AUTH_AUTODETECT}. Rejected in other modes.
 *                                 Default is empty.
 * @param defaultChallengePlane    Plane used in {@link AuthMode#PARTIAL_AUTH_AUTODETECT} when no client
 *                                 hint matches. Default is {@link ChallengePlane#IN_META}.
 */
@Validated
@ConfigurationProperties(prefix = AuthProperties.PREFIX)
public record AuthProperties(
        boolean enabled,
        @Nullable URI authUrl,
        Duration connectTimeout,
        Duration readTimeout,
        boolean overrideExternalResponse,
        @NestedConfigurationProperty @Valid OAuthProperties oauth,
        AuthMode mode,
        @Valid @NestedConfigurationProperty List<ChallengeHint> clientHints,
        ChallengePlane defaultChallengePlane) {

    public static final String PREFIX = "infobip.openapi.mcp.security.auth";

    /**
     * Constructor with defaults for optional properties.
     */
    public AuthProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (mode == null) {
            mode = AuthMode.ALL_OR_NOTHING;
        }
        if (clientHints == null) {
            clientHints = List.of();
        }
        if (defaultChallengePlane == null) {
            defaultChallengePlane = ChallengePlane.IN_META;
        }
    }

    @AssertTrue(message = "infobip.openapi.mcp.security.auth.auth-url is required in ALL_OR_NOTHING mode")
    public boolean isAuthUrlPresent() {
        return mode != AuthMode.ALL_OR_NOTHING || authUrl != null;
    }

    @AssertTrue(
            message = "infobip.openapi.mcp.security.auth.client-hints are only allowed in PARTIAL_AUTH_AUTODETECT mode")
    public boolean isClientHintsConsistentWithMode() {
        return clientHints.isEmpty() || mode == AuthMode.PARTIAL_AUTH_AUTODETECT;
    }

    /**
     * Defines where authorization of MCP requests is decided.
     */
    public enum AuthMode {

        /**
         * Every request is validated against the configured {@code auth-url} before reaching the MCP
         * transport. Default, and the historically only behavior.
         */
        ALL_OR_NOTHING,

        /**
         * The framework validates nothing; the backing HTTP API decides per call, and its {@code 401}/
         * {@code 403} answers are remapped into HTTP {@code 401}/{@code 403} + {@code WWW-Authenticate}
         * challenges — the plane hosts such as Claude act on.
         */
        PARTIAL_AUTH_CLAUDE_COMPATIBLE,

        /**
         * Same API authority, but the challenge travels in-band: {@code isError} result carrying
         * {@code _meta["mcp/www_authenticate"]}, tools listed with {@code _meta.securitySchemes} — the
         * contract of ChatGPT-style hosts, on every transport.
         */
        PARTIAL_AUTH_OPENAI_COMPATIBLE,

        /**
         * Same API authority; the delivery plane is chosen per request from {@code client-hints} and
         * built-in known-client patterns, falling back to {@code default-challenge-plane} and to in-band
         * wherever an HTTP challenge cannot be produced.
         */
        PARTIAL_AUTH_AUTODETECT;

        /**
         * Whether this mode skips upfront validation and lets the backing API decide authorization.
         */
        public boolean isPartialAuth() {
            return this != ALL_OR_NOTHING;
        }

        /**
         * Whether this mode may deliver authorization failures on the HTTP challenge plane.
         */
        public boolean isHttpChallengeCapable() {
            return this == PARTIAL_AUTH_CLAUDE_COMPATIBLE || this == PARTIAL_AUTH_AUTODETECT;
        }

        /**
         * Whether tools are listed with the {@code _meta.securitySchemes} advertisement in this mode.
         */
        public boolean advertisesSecuritySchemes() {
            return this == PARTIAL_AUTH_OPENAI_COMPATIBLE || this == PARTIAL_AUTH_AUTODETECT;
        }
    }

    /**
     * A rule assigning a {@link ChallengePlane} to matching requests, evaluated in configuration order
     * before the built-in patterns; a rule matches when every pattern it configures matches.
     *
     * @param userAgentPattern  regex searched case-insensitively in the {@code User-Agent} header, may be {@code null}
     * @param clientNamePattern regex searched case-insensitively in the {@code initialize} client name, may be {@code null}
     * @param plane             the plane authorization failures of matching requests take
     */
    public record ChallengeHint(
            @Nullable String userAgentPattern,
            @Nullable String clientNamePattern,
            @NotNull ChallengePlane plane) {}
}
