package com.infobip.openapi.mcp.auth.oauth;

import com.infobip.openapi.mcp.auth.web.CallbackPolicy;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test {@link CallbackPolicy} that accepts only redirect URIs whose hostname is one of the expected hostnames. The
 * comparison is case-insensitive and always against the whole hostname, URIs without a hostname are rejected.
 */
class HostnameCallbackPolicy implements CallbackPolicy {

    private final Set<String> allowedHostnames;

    HostnameCallbackPolicy(String... allowedHostnames) {
        this.allowedHostnames = Arrays.stream(allowedHostnames)
                .map(hostname -> hostname.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean test(URI uri) {
        var hostname = uri.getHost();
        return hostname != null && allowedHostnames.contains(hostname.toLowerCase(Locale.ROOT));
    }
}
