package com.infobip.openapi.mcp.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Creates the {@link ClientHttpRequestFactory} used by every {@code RestClient} that calls out to another server
 * (downstream API, authorization endpoint, OAuth server).
 * <p>
 * The factory is backed by the JDK {@link HttpClient}, which, unlike {@code HttpURLConnection}, supports every HTTP
 * method (notably {@code PATCH}). Redirects are followed for {@code GET} requests only, as {@code HttpURLConnection}
 * did. The JDK client sets its redirect policy per client rather than per request, so two clients are kept and one is
 * picked per request method.
 */
@NullMarked
public final class HttpRequestFactories {

    private HttpRequestFactories() {}

    public static ClientHttpRequestFactory create(Duration connectTimeout, Duration readTimeout) {
        var redirectFollowing = create(HttpClient.Redirect.NORMAL, connectTimeout, readTimeout);
        var redirectIgnoring = create(HttpClient.Redirect.NEVER, connectTimeout, readTimeout);
        return (uri, httpMethod) -> HttpMethod.GET.equals(httpMethod)
                ? redirectFollowing.createRequest(uri, httpMethod)
                : redirectIgnoring.createRequest(uri, httpMethod);
    }

    private static JdkClientHttpRequestFactory create(
            HttpClient.Redirect redirectPolicy, Duration connectTimeout, Duration readTimeout) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(redirectPolicy)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
