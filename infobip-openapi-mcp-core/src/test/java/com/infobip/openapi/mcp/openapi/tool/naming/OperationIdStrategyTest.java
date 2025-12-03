package com.infobip.openapi.mcp.openapi.tool.naming;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OperationIdStrategyTest {

    private final OperationIdStrategy strategy = new OperationIdStrategy();

    @Test
    void shouldReturnOperationIdWhenPresent() {
        // Given
        var operation = new Operation().operationId("getUserById");
        var fullOperation = new FullOperation("/users/{id}", PathItem.HttpMethod.GET, operation, new OpenAPI());

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo("getUserById");
    }

    @Test
    void shouldThrowExceptionWhenOperationIdIsNull() {
        // Given
        var operation = new Operation(); // no operationId set
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, operation, new OpenAPI());

        // When & Then
        thenThrownBy(() -> strategy.name(fullOperation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation ID is null - cannot determine how to proceed with naming.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n", "   \t\n  "})
    void shouldThrowExceptionWhenOperationIdIsEmptyOrWhitespace(String operationId) {
        // Given
        var operation = new Operation().operationId(operationId);
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, operation, new OpenAPI());

        // When & Then
        thenThrownBy(() -> strategy.name(fullOperation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Operation ID is empty or contains only whitespace - cannot determine how to proceed with naming.");
    }

    @Test
    void shouldReturnOperationIdAsIsWithoutSanitization() {
        // Given
        var operation = new Operation().operationId("Create-User  Profile&&Settings!!");
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, operation, new OpenAPI());

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo("Create-User  Profile&&Settings!!");
    }
}
