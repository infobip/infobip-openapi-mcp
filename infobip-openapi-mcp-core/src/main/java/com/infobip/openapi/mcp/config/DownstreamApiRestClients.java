package com.infobip.openapi.mcp.config;

import com.infobip.openapi.mcp.util.PlusAwareUriEncoder;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Creates a {@link RestClient.Builder} for calling a downstream API, pre-configured with the given
 * connect/read timeouts and a URI builder that performs no encoding of its own for the given resolved
 * base URL — i.e. a {@code RestClient} whose callers pre-encode query and path parameter values
 * themselves via {@link PlusAwareUriEncoder}. Callers may override {@code .requestFactory(...)} on the
 * returned builder before calling {@code .build()}.
 * <p>
 * Spring's own URI encoders never percent-encode a literal {@code +} (see {@link PlusAwareUriEncoder}),
 * so relying on them would either send an unencoded {@code +} or, once callers pre-encode it
 * themselves, double-encode the result. Disabling encoding here and pre-encoding once at the call
 * site avoids both.
 */
@NullMarked
public final class DownstreamApiRestClients {

    private DownstreamApiRestClients() {}

    public static RestClient.Builder builder(String resolvedBaseUrl, Duration connectTimeout, Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());

        var uriBuilderFactory = new DefaultUriBuilderFactory(resolvedBaseUrl);
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        return RestClient.builder().requestFactory(requestFactory).uriBuilderFactory(uriBuilderFactory);
    }
}
