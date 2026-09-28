package com.infobip.openapi.mcp.util;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.UriUtils;

/**
 * Encodes dynamic values (tool call arguments, prompt arguments) for safe inclusion in a URI's
 * query or path components.
 * <p>
 * Spring's own URI encoders treat {@code +} as an allowed sub-delimiter and never percent-encode
 * it, even though many downstream APIs interpret an unencoded {@code +} in a URI as a literal space
 * (per {@code application/x-www-form-urlencoded} conventions). This is most commonly seen with
 * date-time values carrying a {@code +HH:mm} timezone offset, e.g. {@code 2026-09-25T12:00:00.000+03:00}.
 */
@NullMarked
public final class PlusAwareUriEncoder {

    private PlusAwareUriEncoder() {}

    public static @Nullable String encodeQueryParam(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8).replace("+", "%2B");
    }

    public static @Nullable String encodePathSegment(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8).replace("+", "%2B");
    }
}
