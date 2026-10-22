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
        startWireMockServer();

        String oAuthUrl = staticWireMockServer.baseUrl();
        registry.add("infobip.openapi.mcp.security.auth.oauth.url", () -> oAuthUrl);
    }

    /**
     * Makes sure the shared WireMock server is running. {@code @DynamicPropertySource} methods of subclasses may run
     * before the ones declared here, so they must call this before stubbing anything that is read at startup.
     */
    protected static void startWireMockServer() {
        if (staticWireMockServer == null) {
            staticWireMockServer = new WireMockServer(0);
        }
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }
    }

    protected void reloadOpenApi(String customOpenApiSpec) {
        setupCustomOpenAPIMock(getStaticWireMockServer(), bumpVersion(customOpenApiSpec));
        openApiRegistry.reload();
    }
}
