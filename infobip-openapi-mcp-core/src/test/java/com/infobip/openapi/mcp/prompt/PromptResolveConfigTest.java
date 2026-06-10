package com.infobip.openapi.mcp.prompt;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import org.junit.jupiter.api.Test;

class PromptResolveConfigTest {

    @Test
    void shouldRejectBlankPath() {
        thenThrownBy(() -> new PromptResolveConfig("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void shouldRejectNullPath() {
        thenThrownBy(() -> new PromptResolveConfig(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPathWithoutLeadingSlashOrScheme() {
        thenThrownBy(() -> new PromptResolveConfig("prompts/greet"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with");
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        thenThrownBy(() -> new PromptResolveConfig("ftp://server/prompts/greet"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with");
    }

    @Test
    void shouldAcceptHttpAbsoluteUrl() {
        var config = new PromptResolveConfig("http://other-service.internal/prompts/greet");
        then(config.isAbsolute()).isTrue();
    }

    @Test
    void shouldAcceptHttpsAbsoluteUrl() {
        var config = new PromptResolveConfig("https://other-service.internal/prompts/greet");
        then(config.isAbsolute()).isTrue();
    }

    @Test
    void shouldMarkRelativePathAsNotAbsolute() {
        var config = new PromptResolveConfig("/prompts/greet");
        then(config.isAbsolute()).isFalse();
    }

    @Test
    void shouldAcceptValidRelativePath() {
        new PromptResolveConfig("/prompts/greet");
    }

    @Test
    void shouldAcceptPathWithQueryStringCharacters() {
        new PromptResolveConfig("/prompts/greet?foo=bar");
    }

    @Test
    void shouldAcceptAbsoluteUrlWithQueryStringCharacters() {
        var config = new PromptResolveConfig("https://other-service.internal/prompts/greet?foo=bar");
        then(config.isAbsolute()).isTrue();
    }
}
