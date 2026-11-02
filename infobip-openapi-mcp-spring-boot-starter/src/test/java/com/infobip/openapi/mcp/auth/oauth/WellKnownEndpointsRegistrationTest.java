package com.infobip.openapi.mcp.auth.oauth;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.web.Spec;
import com.infobip.openapi.mcp.auth.web.WellKnownEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the well-known endpoints are registered by auto-configuration alone, i.e. for applications whose
 * component scan doesn't cover the framework's packages.
 */
@ActiveProfiles("test-http")
@SpringBootTest(
        classes = WellKnownEndpointsRegistrationTest.NarrowScanApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WellKnownEndpointsRegistrationTest extends OAuthTestBase {

    @SpringBootApplication
    static class NarrowScanApplication {}

    @Test
    void shouldRegisterWellKnownEndpointsOnceWithoutComponentScanningFrameworkPackages() {
        // When
        var endpoints = applicationContext.getBeansOfType(WellKnownEndpoints.class);
        var response = restTemplate.exchange(
                "http://localhost:" + port + Spec.WellKnownPaths.OAUTH_PROTECTED_RESOURCE,
                HttpMethod.GET,
                null,
                String.class);

        // Then
        then(endpoints).hasSize(1);
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
