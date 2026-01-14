package com.infobip.openapi.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

import com.infobip.openapi.mcp.config.ApiBaseUrlProvider.ApiBaseUrlResolutionException;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.net.URI;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiBaseUrlProviderTest {

    @Mock
    private OpenApiRegistry givenOpenApiRegistry;

    @Test
    void shouldResolveExplicitUrl() {
        // given
        var givenConfig = new ApiBaseUrlConfig.Explicit("https://api.example.com");
        var givenResolver = new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry);

        // when
        var result = givenResolver.get();

        // then
        then(result).isEqualTo(URI.create("https://api.example.com"));
    }

    @Test
    void shouldResolveDefaultToFirstServer() {
        // given
        var givenConfig = new ApiBaseUrlConfig.Default();
        var givenOpenApi = givenOpenApiWithServer("https://api1.example.com", "https://api2.example.com");

        given(givenOpenApiRegistry.openApi()).willReturn(givenOpenApi);
        var givenResolver = new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry);

        // when
        var result = givenResolver.get();

        // then
        then(result).isEqualTo(URI.create("https://api1.example.com"));
    }

    @Test
    void shouldResolveServerIndexZero() {
        // given
        var givenConfig = new ApiBaseUrlConfig.ServerIndex(0);
        var givenOpenApi = givenOpenApiWithServer("https://api1.example.com", "https://api2.example.com");

        given(givenOpenApiRegistry.openApi()).willReturn(givenOpenApi);
        var givenResolver = new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry);

        // when
        var result = givenResolver.get();

        // then
        then(result).isEqualTo(URI.create("https://api1.example.com"));
    }

    @Test
    void shouldResolveServerIndexOne() {
        // given
        var givenConfig = new ApiBaseUrlConfig.ServerIndex(1);
        var givenOpenApi = givenOpenApiWithServer("https://api1.example.com", "https://api2.example.com");

        given(givenOpenApiRegistry.openApi()).willReturn(givenOpenApi);
        var givenResolver = new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry);

        // when
        var result = givenResolver.get();

        // then
        then(result).isEqualTo(URI.create("https://api2.example.com"));
    }

    @Test
    void shouldThrowExceptionWhenNoServersAndDefaultConfig() {
        // given
        var givenConfig = new ApiBaseUrlConfig.Default();
        var givenOpenApi = new OpenAPI();

        given(givenOpenApiRegistry.openApi()).willReturn(givenOpenApi);

        // when
        var thrown = catchThrowable(() -> new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry));

        // then
        then(thrown).isInstanceOf(ApiBaseUrlResolutionException.class);
    }

    @Test
    void shouldThrowExceptionWhenServerIndexOutOfBounds() {
        // given
        var givenConfig = new ApiBaseUrlConfig.ServerIndex(2);
        var givenOpenApi = givenOpenApiWithServer("https://api1.example.com", "https://api2.example.com");

        given(givenOpenApiRegistry.openApi()).willReturn(givenOpenApi);

        // when
        var thrown = catchThrowable(() -> new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry));

        // then
        then(thrown).isInstanceOf(ApiBaseUrlResolutionException.class);
    }

    @Test
    void shouldThrowExceptionWhenExplicitUrlIsInvalid() {
        // given
        var givenConfig = new ApiBaseUrlConfig.Explicit("not a valid uri with spaces");

        // when
        var thrown = catchThrowable(() -> new ApiBaseUrlProvider(givenConfig, givenOpenApiRegistry));

        // then
        then(thrown).isInstanceOf(ApiBaseUrlResolutionException.class);
    }

    @Test
    void shouldParseNullAsDefault() {
        // when
        var givenConfig = ApiBaseUrlConfig.parse(null);

        // then
        then(givenConfig).isInstanceOf(ApiBaseUrlConfig.Default.class);
    }

    @Test
    void shouldParseBlankAsDefault() {
        // when
        var givenConfig = ApiBaseUrlConfig.parse("   ");

        // then
        then(givenConfig).isInstanceOf(ApiBaseUrlConfig.Default.class);
    }

    @Test
    void shouldParseIntegerWithWhitespaceAsServerIndex() {
        // when
        var givenConfig = ApiBaseUrlConfig.parse("  2  ");

        // then
        then(givenConfig).isInstanceOf(ApiBaseUrlConfig.ServerIndex.class);
        then(((ApiBaseUrlConfig.ServerIndex) givenConfig).index()).isEqualTo(2);
    }

    @Test
    void shouldThrowExceptionWhenParsingNegativeInteger() {
        // when
        var thrown = catchThrowable(() -> ApiBaseUrlConfig.parse(" -1 "));

        // then
        then(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldParseUrlAsExplicit() {
        // when
        var givenConfig = ApiBaseUrlConfig.parse("https://api.example.com");

        // then
        assertThat(givenConfig).isInstanceOf(ApiBaseUrlConfig.Explicit.class);
        assertThat(((ApiBaseUrlConfig.Explicit) givenConfig).url()).isEqualTo("https://api.example.com");
    }

    @Test
    void shouldThrowExceptionWhenUrlNotAbsolute() {
        // when
        var thrown1 = catchThrowable(() -> ApiBaseUrlConfig.parse("/"));
        var thrown2 = catchThrowable(() -> ApiBaseUrlConfig.parse("api/example"));
        var thrown3 = catchThrowable(() -> ApiBaseUrlConfig.parse("/relative/path"));
        var thrown4 = catchThrowable(() -> ApiBaseUrlConfig.parse("www.example.com/api"));

        // then
        then(thrown1).isInstanceOf(IllegalArgumentException.class);
        then(thrown2).isInstanceOf(IllegalArgumentException.class);
        then(thrown3).isInstanceOf(IllegalArgumentException.class);
        then(thrown4).isInstanceOf(IllegalArgumentException.class);
    }

    private OpenAPI givenOpenApiWithServer(String... urls) {
        var givenOpenApi = new OpenAPI();
        var servers = new ArrayList<Server>();
        for (var url : urls) {
            var server = new Server();
            server.setUrl(url);
            servers.add(server);
        }
        givenOpenApi.setServers(servers);
        return givenOpenApi;
    }
}
