package com.infobip.openapi.mcp.apps;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppMetaTest {

    @Test
    void shouldProduceEmptyUiMetaForDefaultExtension() {
        then(AppMeta.resourceUiMeta(AppResourceExtension.defaults())).isEmpty();
    }

    @Test
    void shouldProduceUiMetaWithAllConfiguredFields() {
        // Given
        var extension = new AppResourceExtension(
                "ui://dashboard",
                new AppResourceExtension.Csp(
                        List.of("https://api.example.com"), List.of("https://cdn.example.com"), null, null),
                new AppResourceExtension.Permissions(Map.of(), null, Map.of(), null),
                "sandbox.example.com",
                Boolean.TRUE);

        // When
        var uiMeta = AppMeta.resourceUiMeta(extension);

        // Then
        then(uiMeta)
                .containsEntry(
                        "csp",
                        Map.of(
                                "connectDomains", List.of("https://api.example.com"),
                                "resourceDomains", List.of("https://cdn.example.com")))
                .containsEntry("permissions", Map.of("camera", Map.of(), "geolocation", Map.of()))
                .containsEntry("domain", "sandbox.example.com")
                .containsEntry("prefersBorder", Boolean.TRUE)
                .doesNotContainKey("uri");
    }

    @Test
    void shouldOmitEmptyCspListsAndNotDeclaredPermissions() {
        // Given
        var extension = new AppResourceExtension(
                null,
                new AppResourceExtension.Csp(List.of(), null, null, null),
                new AppResourceExtension.Permissions(null, null, null, null),
                null,
                null);

        // When / Then
        then(AppMeta.resourceUiMeta(extension)).isEmpty();
    }

    @Test
    void shouldProduceToolMetaWithNestedAndDeprecatedResourceUri() {
        // Given
        var extension = new AppToolExtension("ui://dashboard", null);

        // When
        var meta = AppMeta.toolMeta(extension);

        // Then
        then(meta)
                .containsEntry(AppMeta.UI_META_KEY, Map.of("resourceUri", "ui://dashboard"))
                .containsEntry(AppMeta.DEPRECATED_TOOL_RESOURCE_URI_KEY, "ui://dashboard")
                .hasSize(2);
    }

    @Test
    void shouldIncludeVisibilityInToolMetaWhenConfigured() {
        // Given
        var extension = new AppToolExtension(
                "ui://dashboard", List.of(AppToolExtension.Visibility.APP, AppToolExtension.Visibility.MODEL));

        // When
        var meta = AppMeta.toolMeta(extension);

        // Then
        then(castUi(meta)).containsEntry("visibility", List.of("app", "model"));
    }

    @Test
    void shouldMergeUiIntoExistingContentMetaWithUiWinning() {
        // Given
        var existingMeta = new java.util.LinkedHashMap<String, Object>();
        existingMeta.put("custom", "value");
        existingMeta.put("ui", "stale value replaced by the extension");
        var extension = new AppResourceExtension(
                null, null, new AppResourceExtension.Permissions(Map.of(), null, null, null), null, null);

        // When
        var merged = AppMeta.contentMeta(existingMeta, extension);

        // Then
        then(merged)
                .containsEntry("custom", "value")
                .containsEntry("ui", Map.of("permissions", Map.of("camera", Map.of())));
    }

    @Test
    void shouldProduceEmptyContentMetaForDefaultExtensionWithoutExistingMeta() {
        then(AppMeta.contentMeta(null, AppResourceExtension.defaults())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castUi(Map<String, Object> meta) {
        return (Map<String, Object>) meta.get(AppMeta.UI_META_KEY);
    }
}
