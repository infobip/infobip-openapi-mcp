package com.infobip.openapi.mcp.auth;

import static org.assertj.core.api.BDDAssertions.then;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void shouldApplyDefaultsForOptionalValues() {
        var properties =
                new AuthProperties(true, URI.create("http://api/validate"), null, null, false, null, null, null, null);

        then(properties.mode()).isEqualTo(AuthProperties.AuthMode.ALL_OR_NOTHING);
        then(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        then(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        then(properties.clientHints()).isEmpty();
        then(properties.defaultChallengePlane()).isEqualTo(ChallengePlane.IN_META);
    }

    @Test
    void shouldRequireAuthUrlOnlyInAllOrNothingMode() {
        var apiDriven = new AuthProperties(
                true,
                null,
                null,
                null,
                false,
                null,
                AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE,
                null,
                null);
        var apiDrivenMeta = new AuthProperties(
                true,
                null,
                null,
                null,
                false,
                null,
                AuthProperties.AuthMode.PARTIAL_AUTH_OPENAI_COMPATIBLE,
                null,
                null);
        var allOrNothingWithoutUrl = new AuthProperties(true, null, null, null, false, null, null, null, null);
        var allOrNothingWithUrl =
                new AuthProperties(true, URI.create("http://api/validate"), null, null, false, null, null, null, null);

        then(apiDriven.isAuthUrlPresent()).isTrue();
        then(apiDrivenMeta.isAuthUrlPresent()).isTrue();
        then(allOrNothingWithoutUrl.isAuthUrlPresent()).isFalse();
        then(allOrNothingWithUrl.isAuthUrlPresent()).isTrue();
    }

    @Test
    void shouldAllowClientHintsOnlyInAutodetectMode() {
        var hints = List.of(new AuthProperties.ChallengeHint("claude-code", null, ChallengePlane.HTTP));

        var autodetect = new AuthProperties(
                true,
                null,
                null,
                null,
                false,
                null,
                AuthProperties.AuthMode.PARTIAL_AUTH_AUTODETECT,
                hints,
                ChallengePlane.HTTP);
        var allOrNothingWithHints =
                new AuthProperties(true, URI.create("http://api/validate"), null, null, false, null, null, hints, null);
        var openAiWithHints = new AuthProperties(
                true,
                null,
                null,
                null,
                false,
                null,
                AuthProperties.AuthMode.PARTIAL_AUTH_OPENAI_COMPATIBLE,
                hints,
                null);

        then(autodetect.isClientHintsConsistentWithMode()).isTrue();
        then(autodetect.defaultChallengePlane()).isEqualTo(ChallengePlane.HTTP);
        then(allOrNothingWithHints.isClientHintsConsistentWithMode()).isFalse();
        then(openAiWithHints.isClientHintsConsistentWithMode()).isFalse();
    }
}
