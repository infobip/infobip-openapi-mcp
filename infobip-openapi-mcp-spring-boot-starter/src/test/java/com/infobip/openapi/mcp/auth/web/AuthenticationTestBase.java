package com.infobip.openapi.mcp.auth.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for authentication integration tests.
 * Extends the common OpenAPITestBase and provides a minimal OpenAPI spec for auth testing.
 */
public abstract class AuthenticationTestBase extends OpenApiTestBase {

    @DynamicPropertySource
    static void configureAuthProperties(DynamicPropertyRegistry registry) {
        if (staticWireMockServer == null) {
            staticWireMockServer = new WireMockServer(0);
        }
        if (!staticWireMockServer.isRunning()) {
            staticWireMockServer.start();
        }

        String authUrl = staticWireMockServer.baseUrl() + "/auth/validate";
        registry.add("infobip.openapi.mcp.security.auth.auth-url", () -> authUrl);
    }
}
