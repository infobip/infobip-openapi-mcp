package com.infobip.openapi.mcp.auth.oauth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class OAuthTestBase extends OpenApiTestBase {

    @Autowired
    protected OpenApiRegistry openApiRegistry;

    @DynamicPropertySource
    static void configureAuthProperties(DynamicPropertyRegistry registry) {
        if (staticWireMockServer == null) {
            staticWireMockServer = new WireMockServer(0);
        }
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }

        String oAuthUrl = staticWireMockServer.baseUrl();
        registry.add("infobip.openapi.mcp.security.auth.oauth.url", () -> oAuthUrl);
    }

    protected void reloadOpenApi(String customOpenApiSpec) {
        setupCustomOpenAPIMock(getStaticWireMockServer(), customOpenApiSpec);
        openApiRegistry.reload();
    }
}
