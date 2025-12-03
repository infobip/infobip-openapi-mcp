package com.infobip.openapi.mcp.error;

import static org.assertj.core.api.BDDAssertions.then;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class DefaultErrorModelProviderTest {

    @Mock
    private HttpServletRequest httpServletRequest;

    private final DefaultErrorModelProvider defaultErrorModelProvider = new DefaultErrorModelProvider();

    @Test
    void shouldProvideUnauthorizedErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.UNAUTHORIZED;

        // When
        var result =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Auth failed"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unauthorized");
        then(result.description()).isEqualTo("Authentication required. Please provide valid credentials.");
    }

    @Test
    void shouldProvideForbiddenErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.FORBIDDEN;

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Access denied"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Forbidden");
        then(result.description()).isEqualTo("Access denied. You don't have permission to access this resource.");
    }

    @Test
    void shouldProvideNotFoundErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.NOT_FOUND;

        // When
        var result =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Not found"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Not Found");
        then(result.description()).isEqualTo("The requested resource was not found.");
    }

    @Test
    void shouldProvideBadRequestErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.BAD_REQUEST;

        // When
        var result =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Bad request"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Bad Request");
        then(result.description()).isEqualTo("Check the request syntax and parameters and try again.");
    }

    @Test
    void shouldProvideInternalServerErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.INTERNAL_SERVER_ERROR;

        // When
        var result =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Server error"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Internal Server Error");
        then(result.description()).isEqualTo("An unexpected error occurred on the server.");
    }

    @Test
    void shouldProvideInternalServerErrorModelForUnknownStatusCode() {
        // Given - This test should use a status that doesn't fall into 4xx or 5xx categories
        HttpStatusCode statusCode = HttpStatus.CONTINUE; // 100 - Not in the mapping, not 4xx or 5xx

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Unexpected status"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unexpected Server Response");
        then(result.description()).isEqualTo("Please try again.");
    }

    @Test
    void shouldHandleNullRequestAndCause() {
        // Given
        HttpStatusCode statusCode = HttpStatus.UNAUTHORIZED;

        // When
        var result = defaultErrorModelProvider.provide(statusCode, null, null);

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unauthorized");
        then(result.description()).isEqualTo("Authentication required. Please provide valid credentials.");
    }

    @Test
    void shouldProvideServiceUnavailableAsInternalServerError() {
        // Given
        HttpStatusCode statusCode = HttpStatus.SERVICE_UNAVAILABLE;

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Service unavailable"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Service Unavailable");
        then(result.description()).isEqualTo("An unexpected server error occurred. Please try again later.");
    }

    @Test
    void shouldProvideClientErrorForUnmapped4xxStatus() {
        // Given - Testing unmapped 4xx client error
        HttpStatusCode statusCode = HttpStatus.CONFLICT; // 409 - Not in the mapping

        // When
        var result =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Conflict"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Conflict");
        then(result.description()).isEqualTo("A client error occurred. Please check your request.");
    }

    @Test
    void shouldProvideClientErrorForGone() {
        // Given - Testing another unmapped 4xx client error
        HttpStatusCode statusCode = HttpStatus.GONE; // 410 - Not in the mapping

        // When
        var result = defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Gone"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Gone");
        then(result.description()).isEqualTo("A client error occurred. Please check your request.");
    }

    @Test
    void shouldProvideClientErrorForUnprocessableEntity() {
        // Given - Testing 422 Unprocessable Entity
        HttpStatusCode statusCode = HttpStatus.UNPROCESSABLE_ENTITY; // 422 - Not in the mapping

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Unprocessable entity"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unprocessable Entity");
        then(result.description()).isEqualTo("A client error occurred. Please check your request.");
    }

    @Test
    void shouldProvideServerErrorForUnmapped5xxStatus() {
        // Given - Testing unmapped 5xx server error (using Gateway Timeout instead of Bad Gateway since 502 is now
        // mapped)
        HttpStatusCode statusCode = HttpStatus.GATEWAY_TIMEOUT; // 504 - Not in the mapping

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Gateway timeout"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Gateway Timeout");
        then(result.description()).isEqualTo("An unexpected server error occurred. Please try again later.");
    }

    @Test
    void shouldProvideServerErrorForServiceUnavailable() {
        // Given - Testing another unmapped 5xx server error
        HttpStatusCode statusCode = HttpStatus.SERVICE_UNAVAILABLE; // 503 - Not in the mapping

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Service unavailable"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Service Unavailable");
        then(result.description()).isEqualTo("An unexpected server error occurred. Please try again later.");
    }

    @Test
    void shouldProvideUnexpectedErrorFor1xxInformationalStatus() {
        // Given - Testing 1xx informational status (should not normally happen in error scenarios)
        HttpStatusCode statusCode = HttpStatus.CONTINUE; // 100

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Informational status"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unexpected Server Response");
        then(result.description()).isEqualTo("Please try again.");
    }

    @Test
    void shouldProvideUnexpectedErrorFor2xxSuccessStatus() {
        // Given - Testing 2xx success status (should not normally happen in error scenarios)
        HttpStatusCode statusCode = HttpStatus.OK; // 200

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Success status"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unexpected Server Response");
        then(result.description()).isEqualTo("Please try again.");
    }

    @Test
    void shouldProvideUnexpectedErrorFor3xxRedirectionStatus() {
        // Given - Testing 3xx redirection status (should not normally happen in error scenarios)
        HttpStatusCode statusCode = HttpStatus.FOUND; // 302

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Redirection status"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unexpected Server Response");
        then(result.description()).isEqualTo("Please try again.");
    }

    @Test
    void shouldProvideUnexpectedErrorForMovedPermanently() {
        // Given - Testing another 3xx redirection status
        HttpStatusCode statusCode = HttpStatus.MOVED_PERMANENTLY; // 301

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Moved permanently"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Unexpected Server Response");
        then(result.description()).isEqualTo("Please try again.");
    }

    @Test
    void shouldProvideTooManyRequestsErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.TOO_MANY_REQUESTS;

        // When
        var result = defaultErrorModelProvider.provide(
                statusCode, httpServletRequest, new RuntimeException("Too many requests"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Too Many Requests");
        then(result.description()).isEqualTo("Request limit exceeded. Please try again later.");
    }

    @Test
    void shouldProvideBadGatewayErrorModel() {
        // Given
        HttpStatusCode statusCode = HttpStatus.BAD_GATEWAY;

        // When
        var result =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Bad gateway"));

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Bad Gateway");
        then(result.description()).isEqualTo("The server received an invalid response from an upstream server.");
    }

    @Test
    void shouldProvideBadGatewayErrorModelWithNullParameters() {
        // Given
        HttpStatusCode statusCode = HttpStatus.BAD_GATEWAY;

        // When
        var result = defaultErrorModelProvider.provide(statusCode, null, null);

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Bad Gateway");
        then(result.description()).isEqualTo("The server received an invalid response from an upstream server.");
    }

    @Test
    void shouldProvideBadGatewayErrorModelWithThrowableCause() {
        // Given
        HttpStatusCode statusCode = HttpStatus.BAD_GATEWAY;
        RuntimeException cause = new RuntimeException("Upstream service failed");

        // When
        var result = defaultErrorModelProvider.provide(statusCode, httpServletRequest, cause);

        // Then
        then(result).isNotNull();
        then(result.error()).isEqualTo("Bad Gateway");
        then(result.description()).isEqualTo("The server received an invalid response from an upstream server.");
    }

    @Test
    void shouldVerifyBadGatewayIsConsistentlyMapped() {
        // Given
        HttpStatusCode statusCode = HttpStatus.BAD_GATEWAY;

        // When
        var result1 =
                defaultErrorModelProvider.provide(statusCode, httpServletRequest, new RuntimeException("Error 1"));
        var result2 = defaultErrorModelProvider.provide(statusCode, null, null);
        var result3 = defaultErrorModelProvider.provide(statusCode, httpServletRequest, new Exception());

        // Then - All results should be identical
        then(result1).isEqualTo(result2);
        then(result2).isEqualTo(result3);
        then(result1.error()).isEqualTo("Bad Gateway");
        then(result1.description()).isEqualTo("The server received an invalid response from an upstream server.");
    }

    @Test
    void shouldVerifyBadGatewayIsDistinctFromOtherServerErrors() {
        // Given
        HttpStatusCode badGateway = HttpStatus.BAD_GATEWAY;
        HttpStatusCode internalServerError = HttpStatus.INTERNAL_SERVER_ERROR;
        HttpStatusCode serviceUnavailable = HttpStatus.SERVICE_UNAVAILABLE;

        // When
        var badGatewayResult =
                defaultErrorModelProvider.provide(badGateway, httpServletRequest, new RuntimeException("Bad gateway"));
        var internalServerErrorResult = defaultErrorModelProvider.provide(
                internalServerError, httpServletRequest, new RuntimeException("Internal server error"));
        var serviceUnavailableResult = defaultErrorModelProvider.provide(
                serviceUnavailable, httpServletRequest, new RuntimeException("Service unavailable"));

        // Then - Bad Gateway should have unique error message
        then(badGatewayResult.error()).isEqualTo("Bad Gateway");
        then(badGatewayResult.description())
                .isEqualTo("The server received an invalid response from an upstream server.");

        // Different from Internal Server Error
        then(badGatewayResult.error()).isNotEqualTo(internalServerErrorResult.error());
        then(badGatewayResult.description()).isNotEqualTo(internalServerErrorResult.description());

        // Different from Service Unavailable
        then(badGatewayResult.error()).isNotEqualTo(serviceUnavailableResult.error());
        then(badGatewayResult.description()).isNotEqualTo(serviceUnavailableResult.description());
    }

    @Test
    void shouldVerifyInternalServerErrorJsonRepresentation() {
        // Given
        String expectedJson =
                "{\"error\":\"Internal Server Error\",\"description\":\"An unexpected error occurred on the server.\"}";

        // When
        String actualJson = DefaultErrorModelProvider.INTERNAL_SERVER_ERROR_JSON_REPRESENTATION;

        // Then
        then(actualJson).isEqualTo(expectedJson);
    }
}
