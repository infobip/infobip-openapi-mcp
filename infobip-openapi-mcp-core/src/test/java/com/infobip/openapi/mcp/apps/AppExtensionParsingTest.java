package com.infobip.openapi.mcp.apps;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the vendor extension records the same way the registries parse them from the
 * OpenAPI model: with a Jackson {@code convertValue} of the YAML/JSON extension value.
 */
class AppExtensionParsingTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void shouldParseFullAppResourceExtension() {
        // Given
        var extensionValue = Map.of(
                "uri",
                "ui://dashboard",
                "csp",
                Map.of(
                        "connectDomains", List.of("https://api.example.com"),
                        "frameDomains", List.of("https://frame.example.com")),
                "permissions",
                Map.of("camera", Map.of(), "clipboardWrite", Map.of()),
                "domain",
                "sandbox.example.com",
                "prefersBorder",
                false);

        // When
        var extension = mapper.convertValue(extensionValue, AppResourceExtension.class);

        // Then
        then(extension)
                .isEqualTo(new AppResourceExtension(
                        "ui://dashboard",
                        new AppResourceExtension.Csp(
                                List.of("https://api.example.com"), null, List.of("https://frame.example.com"), null),
                        new AppResourceExtension.Permissions(Map.of(), null, null, Map.of()),
                        "sandbox.example.com",
                        Boolean.FALSE));
    }

    @Test
    void shouldIgnoreUnknownAppResourceExtensionFields() {
        // Given
        var extensionValue = Map.of("uri", "ui://dashboard", "someFutureField", "ignored");

        // When / Then
        then(mapper.convertValue(extensionValue, AppResourceExtension.class).uri())
                .isEqualTo("ui://dashboard");
    }

    @Test
    void shouldRejectAppResourceUriWithoutUiScheme() {
        // Given
        var extensionValue = Map.of("uri", "https://example.com/dashboard");

        // When / Then
        thenThrownBy(() -> mapper.convertValue(extensionValue, AppResourceExtension.class))
                .isInstanceOf(JacksonException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldParseAppToolExtensionWithoutVisibility() {
        // When
        var extension = mapper.convertValue(Map.of("resourceUri", "ui://dashboard"), AppToolExtension.class);

        // Then
        then(extension).isEqualTo(new AppToolExtension("ui://dashboard", null));
    }

    @Test
    void shouldParseAppToolExtensionVisibility() {
        // When
        var extension = mapper.convertValue(
                Map.of("resourceUri", "ui://dashboard", "visibility", List.of("app")), AppToolExtension.class);

        // Then
        then(extension.visibility()).containsExactly(AppToolExtension.Visibility.APP);
    }

    @Test
    void shouldIgnoreUnknownAppToolExtensionFields() {
        // Given
        var extensionValue = Map.of("resourceUri", "ui://dashboard", "someFutureField", "ignored");

        // When / Then
        then(mapper.convertValue(extensionValue, AppToolExtension.class).resourceUri())
                .isEqualTo("ui://dashboard");
    }

    @Test
    void shouldRejectAppToolExtensionWithBlankResourceUri() {
        thenThrownBy(() -> mapper.convertValue(Map.of("resourceUri", " "), AppToolExtension.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void shouldRejectAppToolExtensionWithMissingResourceUri() {
        thenThrownBy(() -> mapper.convertValue(Map.of(), AppToolExtension.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void shouldRejectAppToolExtensionWithNonUiResourceUri() {
        thenThrownBy(() -> mapper.convertValue(Map.of("resourceUri", "api://dashboard"), AppToolExtension.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void shouldRejectAppToolExtensionWithEmptyVisibility() {
        thenThrownBy(() -> mapper.convertValue(
                        Map.of("resourceUri", "ui://dashboard", "visibility", List.of()), AppToolExtension.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void shouldRejectAppToolExtensionWithUnknownVisibilityValue() {
        thenThrownBy(() -> mapper.convertValue(
                        Map.of("resourceUri", "ui://dashboard", "visibility", List.of("everyone")),
                        AppToolExtension.class))
                .isInstanceOf(JacksonException.class);
    }
}
