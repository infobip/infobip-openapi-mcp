package com.infobip.openapi.mcp.apps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Represents the {@code x-mcp-app} OpenAPI vendor extension, declared on an operation that becomes
 * an MCP tool and links the tool to an MCP Apps UI resource.
 *
 * @param resourceUri the {@code ui://} URI of the UI resource that renders this tool's result
 * @param visibility  the audience the tool is visible to; when unset, the {@code _meta} omits the
 *                    field and the host applies its default ({@code model} and {@code app})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppToolExtension(String resourceUri, @Nullable List<Visibility> visibility) {

    public AppToolExtension {
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new IllegalArgumentException("'x-mcp-app' requires a non-blank 'resourceUri'");
        }
        if (!resourceUri.startsWith(AppMeta.URI_SCHEME)) {
            throw new IllegalArgumentException("'x-mcp-app' resourceUri must start with '" + AppMeta.URI_SCHEME
                    + "', but was '" + resourceUri + "'");
        }
        if (visibility != null) {
            if (visibility.isEmpty()) {
                throw new IllegalArgumentException(
                        "'x-mcp-app' visibility must not be an empty list; omit it to use the host default");
            }
            visibility = List.copyOf(visibility);
        }
    }

    /**
     * Audience the host shows a tool to. The host hides tools whose visibility lacks
     * {@link #MODEL} from the model and rejects app-initiated calls to tools whose visibility lacks
     * {@link #APP}.
     */
    public enum Visibility {
        @JsonProperty("model")
        MODEL,
        @JsonProperty("app")
        APP;

        /**
         * Returns the lower-case wire representation used in the MCP {@code _meta} map.
         */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
