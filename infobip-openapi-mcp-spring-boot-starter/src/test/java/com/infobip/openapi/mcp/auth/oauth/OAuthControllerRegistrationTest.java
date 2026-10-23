package com.infobip.openapi.mcp.auth.oauth;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.autoconfiguration.OAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the controller is registered by auto-configuration alone, i.e. for applications whose component scan
 * doesn't cover the package of the controller.
 */
@ActiveProfiles("test-http")
@SpringBootTest(
        classes = OAuthControllerRegistrationTest.NarrowScanApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthControllerRegistrationTest extends OAuthTestBase {

    @SpringBootApplication
    static class NarrowScanApplication {}

    @Test
    void shouldRegisterControllerOnceWithoutComponentScanningItsPackage() {
        // When
        var controllers = applicationContext.getBeansOfType(OAuthController.class);
        var response = restTemplate.exchange(
                "http://localhost:" + port + OAuthController.OAUTH_PROTECTED_RESOURCE_WELL_KNOWN_PATH,
                HttpMethod.GET,
                null,
                String.class);

        // Then
        then(controllers).hasSize(1);
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
