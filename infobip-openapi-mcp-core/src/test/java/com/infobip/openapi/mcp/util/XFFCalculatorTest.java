package com.infobip.openapi.mcp.util;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class XFFCalculatorTest {

    @Mock
    private HttpServletRequest givenServerRequest;

    private final XFFCalculator xffCalculator = new XFFCalculator();

    @Test
    void shouldAppendClientIpWhenNotIncludedInXForwardedForHeader() {
        // Given
        var givenHeader = "192.0.2.1, 198.51.100.1";
        var givenClientIp = "203.0.113.1";

        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(givenHeader);
        when(givenServerRequest.getRemoteAddr()).thenReturn(givenClientIp);

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isEqualTo("192.0.2.1, 198.51.100.1, 203.0.113.1");
    }

    @Test
    void shouldNotDuplicateClientIpWhenAlreadyIncludedInXForwardedForHeader() {
        // Given
        var givenHeader = "192.0.2.1, 198.51.100.1, 203.0.113.1";
        var givenClientIp = "203.0.113.1";

        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(givenHeader);
        when(givenServerRequest.getRemoteAddr()).thenReturn(givenClientIp);

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isEqualTo(givenHeader);
    }

    @Test
    void shouldReturnClientIpWhenXForwardedForHeaderIsNull() {
        // Given
        var givenClientIp = "203.0.113.1";

        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(givenServerRequest.getRemoteAddr()).thenReturn(givenClientIp);

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isEqualTo(givenClientIp);
    }

    @Test
    void shouldReturnClientIpWhenXForwardedForHeaderIsEmpty() {
        // Given
        var givenClientIp = "203.0.113.1";

        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn("");
        when(givenServerRequest.getRemoteAddr()).thenReturn(givenClientIp);

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isEqualTo(givenClientIp);
    }

    @Test
    void shouldReturnNullWhenBothHeaderAndClientIpAreNull() {
        // Given
        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(givenServerRequest.getRemoteAddr()).thenReturn(null);

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isNull();
    }

    @Test
    void shouldReturnNullWhenBothHeaderAndClientIpAreEmpty() {
        // Given
        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(givenServerRequest.getRemoteAddr()).thenReturn("");

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isNull();
    }

    @Test
    void shouldReturnHeaderWhenClientIpIsNull() {
        // Given
        var givenHeader = "192.0.2.1, 198.51.100.1";

        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(givenHeader);
        when(givenServerRequest.getRemoteAddr()).thenReturn(null);

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isEqualTo(givenHeader);
    }

    @Test
    void shouldReturnHeaderWhenClientIpIsEmpty() {
        // Given
        var givenHeader = "192.0.2.1, 198.51.100.1";

        when(givenServerRequest.getHeader("X-Forwarded-For")).thenReturn(givenHeader);
        when(givenServerRequest.getRemoteAddr()).thenReturn("");

        // When
        var actualResult = xffCalculator.calculateXFF(givenServerRequest);

        // Then
        then(actualResult).isEqualTo(givenHeader);
    }
}
