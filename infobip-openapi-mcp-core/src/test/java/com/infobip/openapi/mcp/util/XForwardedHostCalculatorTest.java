package com.infobip.openapi.mcp.util;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

public class XForwardedHostCalculatorTest {

    public static Stream<Arguments> testDataWithoutHeaders() {
        return Stream.of(
                arguments(
                        "http",
                        "mcp.example.com",
                        80,
                        null,
                        null,
                        null,
                        "http://mcp.example.com",
                        "http://mcp.example.com"),
                arguments(
                        "https",
                        "mcp.example.com",
                        443,
                        null,
                        null,
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com"),
                arguments(
                        "https",
                        "mcp.example.com",
                        8080,
                        null,
                        null,
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080"),
                arguments(
                        "https",
                        "mcp.example.com",
                        443,
                        "/path",
                        null,
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com"),
                arguments(
                        "https",
                        "mcp.example.com",
                        8080,
                        "/path",
                        null,
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080"),
                arguments(
                        "https",
                        "mcp.example.com",
                        443,
                        "/path",
                        "/mcp-root",
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        8080,
                        "/path",
                        "/mcp-root",
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        443,
                        "/path",
                        null,
                        "/mcp-root",
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        8080,
                        "/path",
                        null,
                        "/mcp-root",
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080/mcp-root"));
    }

    @ParameterizedTest
    @MethodSource("testDataWithoutHeaders")
    public void shouldUseRequestUriIfThereAreNoHeaders(
            @Nullable String givenScheme,
            @Nullable String givenServerName,
            @NonNull Integer givenServerPort,
            @Nullable String givenPath,
            @Nullable String givenStreamableEndpoint,
            @Nullable String givenSseEndpoint,
            @NonNull String expectedBaseUri,
            @NonNull String expectedUriWithRootPath) {
        // given
        var givenReq = new MockHttpServletRequest();
        if (givenScheme != null) {
            givenReq.setScheme(givenScheme);
        }
        if (givenServerName != null) {
            givenReq.setServerName(givenServerName);
        }
        givenReq.setServerPort(givenServerPort);
        givenReq.setRequestURI(givenPath);
        var calculator = new XForwardedHostCalculator(
                givenStreamableProps(givenStreamableEndpoint), givenSseProps(givenSseEndpoint));

        // when
        var actualUri = calculator.hostBuilder(givenReq).toUriString();
        var actualUriWithRootPath = calculator.hostWithRootPathBuilder(givenReq).toUriString();

        // then
        then(actualUri).isEqualTo(expectedBaseUri);
        then(actualUriWithRootPath).isEqualTo(expectedUriWithRootPath);
    }

    public static Stream<Arguments> testDataWithoutForwardedHeaders() {
        return Stream.of(
                arguments("mcp.example.com", null, null, null, "https://mcp.example.com", "https://mcp.example.com"),
                arguments("mcp.example.com", "/path", null, null, "https://mcp.example.com", "https://mcp.example.com"),
                arguments(
                        "mcp.example.com",
                        "/path",
                        "/mcp-root",
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        "mcp.example.com",
                        "/path",
                        null,
                        "/mcp-root",
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"));
    }

    @ParameterizedTest
    @MethodSource("testDataWithoutForwardedHeaders")
    public void shouldUseHostHeaderIfThereAreNoForwardedHeaders(
            @NonNull String givenHostHeader,
            @Nullable String givenPath,
            @Nullable String givenStreamableEndpoint,
            @Nullable String givenSseEndpoint,
            @NonNull String expectedBaseUri,
            @NonNull String expectedUriWithRootPath) {
        // given
        var givenReq = new MockHttpServletRequest();
        givenReq.setScheme("https");
        givenReq.setServerName("example.local");
        givenReq.setServerPort(443);
        givenReq.setRequestURI(givenPath);
        givenReq.addHeader(HttpHeaders.HOST, givenHostHeader);
        var calculator = new XForwardedHostCalculator(
                givenStreamableProps(givenStreamableEndpoint), givenSseProps(givenSseEndpoint));

        // when
        var actualUri = calculator.hostBuilder(givenReq).toUriString();
        var actualUriWithRootPath = calculator.hostWithRootPathBuilder(givenReq).toUriString();

        // then
        then(actualUri).isEqualTo(expectedBaseUri);
        then(actualUriWithRootPath).isEqualTo(expectedUriWithRootPath);
    }

    public static Stream<Arguments> testDataWithAllHeaders() {
        return Stream.of(
                arguments(
                        null,
                        "mcp.example.com",
                        null,
                        null,
                        null,
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com"),
                arguments(
                        "http",
                        "mcp.example.com",
                        null,
                        null,
                        null,
                        null,
                        "http://mcp.example.com",
                        "http://mcp.example.com"),
                arguments(
                        "https",
                        "mcp.example.com",
                        "8080",
                        null,
                        null,
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080"),
                arguments(
                        null,
                        "mcp.example.com",
                        null,
                        "/path",
                        null,
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com"),
                arguments(
                        "http",
                        "mcp.example.com",
                        null,
                        "/path",
                        null,
                        null,
                        "http://mcp.example.com",
                        "http://mcp.example.com"),
                arguments(
                        "https",
                        "mcp.example.com",
                        "8080",
                        "/path",
                        null,
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080"),
                arguments(
                        null,
                        "mcp.example.com",
                        null,
                        null,
                        "/mcp-root",
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        null,
                        "mcp.example.com",
                        null,
                        null,
                        null,
                        "/mcp-root",
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        "http",
                        "mcp.example.com",
                        null,
                        null,
                        "/mcp-root",
                        null,
                        "http://mcp.example.com",
                        "http://mcp.example.com/mcp-root"),
                arguments(
                        "http",
                        "mcp.example.com",
                        null,
                        null,
                        null,
                        "/mcp-root",
                        "http://mcp.example.com",
                        "http://mcp.example.com/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        "8080",
                        null,
                        "/mcp-root",
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        "8080",
                        null,
                        null,
                        "/mcp-root",
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080/mcp-root"),
                arguments(
                        null,
                        "mcp.example.com",
                        null,
                        "/path",
                        "/mcp-root",
                        null,
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        null,
                        "mcp.example.com",
                        null,
                        "/path",
                        null,
                        "/mcp-root",
                        "https://mcp.example.com",
                        "https://mcp.example.com/mcp-root"),
                arguments(
                        "http",
                        "mcp.example.com",
                        null,
                        "/path",
                        "/mcp-root",
                        null,
                        "http://mcp.example.com",
                        "http://mcp.example.com/mcp-root"),
                arguments(
                        "http",
                        "mcp.example.com",
                        null,
                        "/path",
                        null,
                        "/mcp-root",
                        "http://mcp.example.com",
                        "http://mcp.example.com/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        "8080",
                        "/path",
                        "/mcp-root",
                        null,
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080/mcp-root"),
                arguments(
                        "https",
                        "mcp.example.com",
                        "8080",
                        "/path",
                        null,
                        "/mcp-root",
                        "https://mcp.example.com:8080",
                        "https://mcp.example.com:8080/mcp-root"));
    }

    @ParameterizedTest
    @MethodSource("testDataWithAllHeaders")
    public void shouldUseForwardedHeaders(
            @Nullable String givenForwardedProto,
            @Nullable String givenForwardedHost,
            @Nullable String givenForwardedPort,
            @Nullable String givenPath,
            @Nullable String givenStreamableEndpoint,
            @Nullable String givenSseEndpoint,
            @NonNull String expectedBaseUri,
            @NonNull String expectedUriWithRootPath) {
        // given
        var givenReq = new MockHttpServletRequest();
        givenReq.setScheme("https");
        givenReq.setServerName("example.local");
        givenReq.setServerPort(443);
        givenReq.setRequestURI(givenPath);
        givenReq.addHeader(HttpHeaders.HOST, "example.local");
        if (givenForwardedProto != null) {
            givenReq.addHeader("x-forwarded-proto", givenForwardedProto);
        }
        if (givenForwardedHost != null) {
            givenReq.addHeader("x-forwarded-host", givenForwardedHost);
        }
        if (givenForwardedPort != null) {
            givenReq.addHeader("x-forwarded-port", givenForwardedPort);
        }
        var calculator = new XForwardedHostCalculator(
                givenStreamableProps(givenStreamableEndpoint), givenSseProps(givenSseEndpoint));

        // when
        var actualUri = calculator.hostBuilder(givenReq).toUriString();
        var actualUriWithRootPath = calculator.hostWithRootPathBuilder(givenReq).toUriString();

        // then
        then(actualUri).isEqualTo(expectedBaseUri);
        then(actualUriWithRootPath).isEqualTo(expectedUriWithRootPath);
    }

    private Optional<McpServerStreamableHttpProperties> givenStreamableProps(@Nullable String streamableEndpoint) {
        if (streamableEndpoint == null) {
            return Optional.empty();
        }

        var props = new McpServerStreamableHttpProperties();
        props.setMcpEndpoint(streamableEndpoint);
        return Optional.of(props);
    }

    private Optional<McpServerSseProperties> givenSseProps(@Nullable String sseEndpoint) {
        if (sseEndpoint == null) {
            return Optional.empty();
        }

        var props = new McpServerSseProperties();
        props.setSseEndpoint(sseEndpoint);
        return Optional.of(props);
    }
}
