package com.infobip.openapi.mcp.openapi.tool.naming;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrimNamingStrategyTest {

    @Mock
    private NamingStrategy delegate;

    private final FullOperation fullOperation =
            new FullOperation("/test", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10, -100})
    void shouldThrowIllegalArgumentExceptionForNonPositiveMaxLength(int invalidMaxLength) {
        // When & Then
        thenThrownBy(() -> new TrimNamingStrategy(delegate, invalidMaxLength))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max length must be positive, but was: " + invalidMaxLength);
    }

    @Test
    void shouldReturnOriginalNameWhenShorterThanMaxLength() {
        // Given
        given(delegate.name(fullOperation)).willReturn("short");
        var strategy = new TrimNamingStrategy(delegate, 10);

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo("short");
    }

    @Test
    void shouldReturnOriginalNameWhenEqualToMaxLength() {
        // Given
        given(delegate.name(fullOperation)).willReturn("exact");
        var strategy = new TrimNamingStrategy(delegate, 5);

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo("exact");
    }

    @Test
    void shouldTrimNameWhenLongerThanMaxLength() {
        // Given
        given(delegate.name(fullOperation)).willReturn("very_long_operation_name");
        var strategy = new TrimNamingStrategy(delegate, 10);

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo("very_long_");
    }

    @Test
    void shouldTrimToSingleCharacterWhenMaxLengthIsOne() {
        // Given
        given(delegate.name(fullOperation)).willReturn("long_name");
        var strategy = new TrimNamingStrategy(delegate, 1);

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo("l");
    }

    @Test
    void shouldHandleEmptyStringFromDelegate() {
        // Given
        given(delegate.name(fullOperation)).willReturn("");
        var strategy = new TrimNamingStrategy(delegate, 5);

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEmpty();
    }
}
