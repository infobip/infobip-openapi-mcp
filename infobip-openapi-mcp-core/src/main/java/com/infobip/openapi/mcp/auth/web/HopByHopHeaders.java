package com.infobip.openapi.mcp.auth.web;

import java.util.Locale;
import java.util.Set;

/**
 * Names of the hop-by-hop HTTP headers (<a href="https://www.rfc-editor.org/rfc/rfc9110#section-7.6.1">RFC 9110
 * section 7.6.1</a>), which are meaningful only for a single transport-level connection and must not be copied from an
 * upstream response to the response sent to the client.
 */
public final class HopByHopHeaders {

    private HopByHopHeaders() {}

    /**
     * Lower-case names of the hop-by-hop headers.
     */
    public static final Set<String> NAMES = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade");

    /**
     * Checks whether the given header is a hop-by-hop header, ignoring the case of the name.
     */
    public static boolean isHopByHop(String headerName) {
        return NAMES.contains(headerName.toLowerCase(Locale.ROOT));
    }
}
