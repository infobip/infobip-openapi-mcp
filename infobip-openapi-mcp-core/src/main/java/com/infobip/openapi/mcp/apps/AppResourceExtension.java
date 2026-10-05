package com.infobip.openapi.mcp.apps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Represents the {@code x-mcp-app-resource} OpenAPI vendor extension, declared on a {@code GET}
 * operation whose response is an HTML document that MCP Apps-capable hosts render as an embedded UI.
 *
 * <p>All fields are optional; the shorthand {@code x-mcp-app-resource: true} is equivalent to an
 * extension with every field unset (see {@link #defaults()}).
 *
 * @param uri           the {@code ui://} URI of the UI resource; defaults to {@code ui://<path>}
 *                      (path without the leading slash) when unset
 * @param csp           origins the UI is allowed to load or communicate with; when unset, hosts
 *                      apply their restrictive default
 * @param permissions   sensitive browser capabilities the UI requests from the host
 * @param domain        dedicated sandbox origin assigned by the host (host-specific format)
 * @param prefersBorder whether the host should draw a border around the view
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppResourceExtension(
        @Nullable String uri,
        @Nullable Csp csp,
        @Nullable Permissions permissions,
        @Nullable String domain,
        @Nullable Boolean prefersBorder) {

    public AppResourceExtension {
        if (uri != null && !uri.startsWith(AppMeta.URI_SCHEME)) {
            throw new IllegalArgumentException(
                    "'uri' must start with '" + AppMeta.URI_SCHEME + "', but was '" + uri + "'");
        }
    }

    /**
     * Returns the extension value equivalent to the {@code x-mcp-app-resource: true} shorthand:
     * a UI resource with all optional fields unset.
     */
    public static AppResourceExtension defaults() {
        return new AppResourceExtension(null, null, null, null, null);
    }

    /**
     * Content Security Policy origins the UI resource declares. Hosts refuse any origin the
     * resource does not declare here, so an omitted field means "no external origins of that kind".
     *
     * @param connectDomains  origins allowed for {@code connect-src} (fetch/XHR/WebSocket)
     * @param resourceDomains origins allowed for scripts, styles, images, fonts and media
     * @param frameDomains    origins allowed for nested iframes ({@code frame-src})
     * @param baseUriDomains  origins allowed for {@code base-uri}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Csp(
            @Nullable List<String> connectDomains,
            @Nullable List<String> resourceDomains,
            @Nullable List<String> frameDomains,
            @Nullable List<String> baseUriDomains) {}

    /**
     * Sensitive browser capabilities the UI resource requests. Each declared permission is
     * represented as an empty object, mirroring the MCP Apps specification.
     *
     * @param camera        the UI requests camera access
     * @param microphone    the UI requests microphone access
     * @param geolocation   the UI requests geolocation access
     * @param clipboardWrite the UI requests clipboard write access
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Permissions(
            @Nullable Map<String, Object> camera,
            @Nullable Map<String, Object> microphone,
            @Nullable Map<String, Object> geolocation,
            @Nullable Map<String, Object> clipboardWrite) {}
}
