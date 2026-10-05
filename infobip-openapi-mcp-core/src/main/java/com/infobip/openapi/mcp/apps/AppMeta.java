package com.infobip.openapi.mcp.apps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constants of the MCP Apps extension ({@code io.modelcontextprotocol/ui}) and converters from the
 * vendor extension records into the MCP {@code _meta} maps emitted on tools, resources and
 * resource contents.
 *
 * <p>Fields that are not configured are omitted from the produced maps, so hosts apply their own
 * defaults instead of a value the user never chose.
 */
@NullMarked
public final class AppMeta {

    /**
     * Identifier of the MCP Apps extension.
     */
    public static final String EXTENSION_ID = "io.modelcontextprotocol/ui";

    /**
     * The exact MIME type a UI resource must declare on {@code resources/read} contents.
     */
    public static final String MIME_TYPE = "text/html;profile=mcp-app";

    /**
     * The URI scheme reserved by the MCP Apps specification for UI resources.
     */
    public static final String URI_SCHEME = "ui://";

    /**
     * The {@code _meta} key under which MCP Apps metadata is published.
     */
    public static final String UI_META_KEY = "ui";

    /**
     * The deprecated flat {@code _meta} key for a tool's UI resource URI, still emitted for
     * backwards compatibility with hosts that predate the nested {@code _meta.ui} form.
     */
    public static final String DEPRECATED_TOOL_RESOURCE_URI_KEY = "ui/resourceUri";

    private AppMeta() {}

    /**
     * Builds the {@code _meta.ui} map of an app resource from its extension: the declared CSP
     * origins, permissions, sandbox domain and border preference. Returns an empty map when the
     * extension declares nothing.
     */
    public static Map<String, Object> resourceUiMeta(AppResourceExtension extension) {
        var ui = new LinkedHashMap<String, Object>();

        var csp = cspMap(extension.csp());
        if (!csp.isEmpty()) {
            ui.put("csp", csp);
        }

        var permissions = permissionsMap(extension.permissions());
        if (!permissions.isEmpty()) {
            ui.put("permissions", permissions);
        }

        if (extension.domain() != null) {
            ui.put("domain", extension.domain());
        }
        if (extension.prefersBorder() != null) {
            ui.put("prefersBorder", extension.prefersBorder());
        }

        return ui;
    }

    /**
     * Builds the complete {@code _meta} map of an app-linked tool: the nested {@code _meta.ui}
     * block with the resource URI and, when configured, the visibility list, plus the deprecated
     * flat {@code ui/resourceUri} key.
     */
    public static Map<String, Object> toolMeta(AppToolExtension extension) {
        var ui = new LinkedHashMap<String, Object>();
        ui.put("resourceUri", extension.resourceUri());
        if (extension.visibility() != null) {
            ui.put(
                    "visibility",
                    extension.visibility().stream()
                            .map(AppToolExtension.Visibility::wireName)
                            .toList());
        }

        var meta = new LinkedHashMap<String, Object>();
        meta.put(UI_META_KEY, ui);
        meta.put(DEPRECATED_TOOL_RESOURCE_URI_KEY, extension.resourceUri());
        return meta;
    }

    /**
     * Merges the {@code _meta.ui} of an app resource into the {@code _meta} of one of its read
     * contents, preserving any metadata a custom contents converter already set. The extension's
     * {@code ui} entry wins over a pre-existing one.
     */
    public static Map<String, Object> contentMeta(
            @Nullable Map<String, Object> existingMeta, AppResourceExtension extension) {
        var merged = new LinkedHashMap<String, Object>();
        if (existingMeta != null) {
            merged.putAll(existingMeta);
        }
        var uiMeta = resourceUiMeta(extension);
        if (!uiMeta.isEmpty()) {
            merged.put(UI_META_KEY, uiMeta);
        }
        return merged;
    }

    private static Map<String, Object> cspMap(AppResourceExtension.@Nullable Csp csp) {
        if (csp == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        putIfNotEmpty(map, "connectDomains", csp.connectDomains());
        putIfNotEmpty(map, "resourceDomains", csp.resourceDomains());
        putIfNotEmpty(map, "frameDomains", csp.frameDomains());
        putIfNotEmpty(map, "baseUriDomains", csp.baseUriDomains());
        return map;
    }

    private static Map<String, Object> permissionsMap(AppResourceExtension.@Nullable Permissions permissions) {
        if (permissions == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        putIfDeclared(map, "camera", permissions.camera());
        putIfDeclared(map, "microphone", permissions.microphone());
        putIfDeclared(map, "geolocation", permissions.geolocation());
        putIfDeclared(map, "clipboardWrite", permissions.clipboardWrite());
        return map;
    }

    private static void putIfNotEmpty(Map<String, Object> target, String key, @Nullable List<String> domains) {
        if (domains != null && !domains.isEmpty()) {
            target.put(key, List.copyOf(domains));
        }
    }

    private static void putIfDeclared(Map<String, Object> target, String key, @Nullable Map<String, Object> value) {
        if (value != null) {
            target.put(key, Map.of());
        }
    }
}
