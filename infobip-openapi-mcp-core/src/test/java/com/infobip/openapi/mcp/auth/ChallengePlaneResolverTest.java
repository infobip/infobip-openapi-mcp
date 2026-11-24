package com.infobip.openapi.mcp.auth;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.AuthProperties.AuthMode;
import com.infobip.openapi.mcp.auth.AuthProperties.ChallengeHint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ChallengePlaneResolverTest {

    private static final ChallengePlaneResolver DEFAULT_IN_META =
            new ChallengePlaneResolver(List.of(), ChallengePlane.IN_META);

    @Test
    void shouldRouteClaudeFamilyClientsToHttpPlane() {
        then(resolve(DEFAULT_IN_META, "claude-code/2.1.311 (cli)", null)).isEqualTo(ChallengePlane.HTTP);
        then(resolve(DEFAULT_IN_META, "Claude-User", null)).isEqualTo(ChallengePlane.HTTP);
        then(resolve(DEFAULT_IN_META, "CLAUDE-Code/1.0", null)).isEqualTo(ChallengePlane.HTTP);
    }

    @Test
    void shouldRouteOpenAiFamilyClientsToInMetaPlane() {
        then(resolve(DEFAULT_IN_META, "openai-mcp/1.0.0 (Responses API)", null)).isEqualTo(ChallengePlane.IN_META);
        then(resolve(DEFAULT_IN_META, "codex-mcp-client/0.171.0-alpha.13", null))
                .isEqualTo(ChallengePlane.IN_META);
    }

    @Test
    void shouldApplyConfiguredDefaultPlaneWhenNothingMatches() {
        then(resolve(DEFAULT_IN_META, "Cursor/1.0.0", "cursor")).isEqualTo(ChallengePlane.IN_META);
        then(resolve(DEFAULT_IN_META, null, null)).isEqualTo(ChallengePlane.IN_META);
        then(resolve(new ChallengePlaneResolver(List.of(), ChallengePlane.HTTP), "SomeEnterpriseGateway/2.0", null))
                .isEqualTo(ChallengePlane.HTTP);
    }

    @Test
    void shouldPrecedeUserHintsOverKnownClients() {
        var resolver = new ChallengePlaneResolver(
                List.of(new ChallengeHint("mcp-gateway", null, ChallengePlane.IN_META)), ChallengePlane.HTTP);

        // matches the built-in claude rule, too, but the configured hint comes first
        then(resolve(resolver, "claude-mcp-gateway/3.0", null)).isEqualTo(ChallengePlane.IN_META);
    }

    @Test
    void shouldRequireAllConfiguredPatternsOfAHintToMatch() {
        var resolver = new ChallengePlaneResolver(
                List.of(new ChallengeHint("claude", "claude code", ChallengePlane.IN_META)), ChallengePlane.IN_META);

        then(resolve(resolver, "claude-code/1.0", "claude code")).isEqualTo(ChallengePlane.IN_META);
        // client name does not match the hint, so it falls through to the built-in claude rule
        then(resolve(resolver, "claude-code/1.0", "claude desktop")).isEqualTo(ChallengePlane.HTTP);
    }

    @Test
    void shouldNotMatchUserAgentHintWithoutUserAgentHeader() {
        var resolver = new ChallengePlaneResolver(
                List.of(new ChallengeHint("claude", null, ChallengePlane.IN_META)), ChallengePlane.HTTP);

        then(resolve(resolver, null, null)).isEqualTo(ChallengePlane.HTTP);
    }

    @Test
    void shouldMatchClientNameHintWhenUserAgentIsAbsent() {
        var resolver = new ChallengePlaneResolver(
                List.of(new ChallengeHint(null, "^cursor", ChallengePlane.HTTP)), ChallengePlane.IN_META);

        then(resolve(resolver, null, "Cursor")).isEqualTo(ChallengePlane.HTTP);
        then(resolve(resolver, null, "some other client")).isEqualTo(ChallengePlane.IN_META);
    }

    @Test
    void shouldKeepHintWithoutPatternsInert() {
        var resolver = new ChallengePlaneResolver(
                List.of(new ChallengeHint(null, null, ChallengePlane.IN_META)), ChallengePlane.HTTP);

        then(resolve(resolver, null, null)).isEqualTo(ChallengePlane.HTTP);
    }

    @Test
    void shouldResolveInMetaWithoutServletRequestWhenDefaultIsInMeta() {
        var request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "claude-code/1.0");

        // no servlet request at all (stdio): client identity is unavailable, so the default plane rules
        then(DEFAULT_IN_META.resolve(null, "claude desktop")).isEqualTo(ChallengePlane.IN_META);
        then(DEFAULT_IN_META.resolve(request, null)).isEqualTo(ChallengePlane.HTTP);
    }

    @Test
    void shouldRejectInvalidUserAgentPatternAtStartup() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ChallengePlaneResolver(
                        List.of(new ChallengeHint("claude([", null, ChallengePlane.HTTP)), ChallengePlane.IN_META))
                .withMessageContaining("user-agent-pattern")
                .withMessageContaining("claude([");
    }

    @Test
    void shouldRejectInvalidClientNamePatternAtStartup() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ChallengePlaneResolver(
                        List.of(new ChallengeHint(null, "*(client", ChallengePlane.HTTP)), ChallengePlane.IN_META))
                .withMessageContaining("client-name-pattern");
    }

    @Test
    void shouldRejectChallengeHintsOutsideAutodetectMode() {
        var withHints = new AuthProperties(
                true,
                null,
                null,
                null,
                false,
                null,
                AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE,
                List.of(new ChallengeHint("claude", null, ChallengePlane.HTTP)),
                null);

        then(withHints.isClientHintsConsistentWithMode()).isFalse();
    }

    private ChallengePlane resolve(ChallengePlaneResolver resolver, String userAgent, String clientName) {
        var request = new MockHttpServletRequest();
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        return resolver.resolve(request, clientName);
    }
}
