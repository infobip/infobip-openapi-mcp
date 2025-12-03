package com.infobip.openapi.mcp.openapi.tool.naming;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;

/**
 * Factory for creating {@link NamingStrategy} instances based on configuration.
 * <p>
 * This factory is responsible for instantiating the appropriate naming strategy
 * implementation based on the provided configuration. It supports creating base
 * strategies and applying decorators such as length trimming.
 * </p>
 *
 * <h3>Supported Strategies:</h3>
 * <ul>
 *   <li>{@link NamingStrategyType#OPERATION_ID} - Creates {@link OperationIdStrategy}</li>
 *   <li>{@link NamingStrategyType#SANITIZED_OPERATION_ID} - Creates {@link SanitizedOperationIdStrategy}</li>
 *   <li>{@link NamingStrategyType#ENDPOINT} - Creates {@link EndpointStrategy}</li>
 * </ul>
 *
 * <h3>Decorators:</h3>
 * <p>
 * The factory can wrap base strategies with additional functionality:
 * </p>
 * <ul>
 *   <li>Length trimming via {@link TrimNamingStrategy} when maxLength is configured</li>
 * </ul>
 *
 * @see NamingStrategy
 * @see NamingStrategyType
 * @see TrimNamingStrategy
 * @see OpenApiMcpProperties.Tools.Naming
 */
public class NamingStrategyFactory {

    /**
     * Creates a {@link NamingStrategy} based on the provided configuration.
     * <p>
     * This method instantiates the appropriate base strategy according to the
     * configured strategy type and applies any additional decorators such as
     * length trimming if specified in the configuration.
     * </p>
     *
     * @param naming the tool naming configuration containing strategy type and optional parameters
     * @return the configured naming strategy, potentially wrapped with decorators
     * @throws IllegalArgumentException if an unsupported strategy type is provided
     */
    public NamingStrategy create(OpenApiMcpProperties.Tools.Naming naming) {
        NamingStrategy baseStrategy =
                switch (naming.strategy()) {
                    case OPERATION_ID -> new OperationIdStrategy();
                    case SANITIZED_OPERATION_ID -> new SanitizedOperationIdStrategy();
                    case ENDPOINT -> new EndpointStrategy();
                };

        if (naming.maxLength() != null) {
            return new TrimNamingStrategy(baseStrategy, naming.maxLength());
        }

        return baseStrategy;
    }
}
