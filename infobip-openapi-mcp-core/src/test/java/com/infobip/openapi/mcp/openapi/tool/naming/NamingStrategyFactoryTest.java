package com.infobip.openapi.mcp.openapi.tool.naming;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import org.junit.jupiter.api.Test;

class NamingStrategyFactoryTest {

    private final NamingStrategyFactory factory = new NamingStrategyFactory();

    @Test
    void shouldCreateOperationIdStrategy() {
        // Given
        var naming = createNaming(NamingStrategyType.OPERATION_ID, null);

        // When
        var result = factory.create(naming);

        // Then
        then(result).isInstanceOf(OperationIdStrategy.class);
    }

    @Test
    void shouldCreateEndpointStrategy() {
        // Given
        var naming = createNaming(NamingStrategyType.ENDPOINT, null);

        // When
        var result = factory.create(naming);

        // Then
        then(result).isInstanceOf(EndpointStrategy.class);
    }

    @Test
    void shouldCreateOperationIdStrategyWithTrimWhenMaxLengthSpecified() {
        // Given
        var naming = createNaming(NamingStrategyType.OPERATION_ID, 25);

        // When
        var result = factory.create(naming);

        // Then
        then(result).isInstanceOf(TrimNamingStrategy.class);
    }

    @Test
    void shouldCreateEndpointStrategyWithTrimWhenMaxLengthSpecified() {
        // Given
        var naming = createNaming(NamingStrategyType.ENDPOINT, 15);

        // When
        var result = factory.create(naming);

        // Then
        then(result).isInstanceOf(TrimNamingStrategy.class);
    }

    @Test
    void shouldCreateEndpointStrategyByDefault() {
        // Given - using default configuration
        var tools = new OpenApiMcpProperties.Tools();
        var naming = tools.naming(); // Uses defaults

        // When
        var result = factory.create(naming);

        // Then
        then(result).isInstanceOf(SanitizedOperationIdStrategy.class);
        then(naming.strategy()).isEqualTo(NamingStrategyType.SANITIZED_OPERATION_ID);
    }

    private OpenApiMcpProperties.Tools.Naming createNaming(NamingStrategyType strategy, Integer maxLength) {
        return new OpenApiMcpProperties.Tools.Naming(strategy, maxLength);
    }
}
