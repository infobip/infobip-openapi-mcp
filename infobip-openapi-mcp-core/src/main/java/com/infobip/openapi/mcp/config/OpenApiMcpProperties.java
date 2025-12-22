package com.infobip.openapi.mcp.config;

import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for OpenAPI MCP Server.
 *
 * @param openApiUrl     URL to the OpenAPI specification. This should point to a valid OpenAPI document (e.g., JSON or YAML).
 * @param apiBaseUrl     Base URL for the API. This is used to construct the full URLs for the API endpoints.
 * @param connectTimeout Connection timeout for HTTP requests to the downstream API. The default is set to 5 seconds.
 * @param readTimeout    Read timeout for HTTP requests to the downstream API. The default is set to 5 seconds.
 * @param userAgent      User agent string for HTTP requests to the downstream API. If not specified, no User-Agent header will be set.
 * @param filters        Filters to apply to the OpenAPI specification. This can be used to include or exclude specific operations or tags.
 *                       The keys are the filter names, and the values are booleans indicating whether to include (true) or exclude (false).
 *                       By default, all filters are enabled.
 * @param tools          Tool configuration.
 */
@Validated
@ConfigurationProperties(prefix = OpenApiMcpProperties.PREFIX)
public record OpenApiMcpProperties(
        @NotNull URI openApiUrl,
        @NotNull URI apiBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String userAgent,
        Map<String, Boolean> filters,
        @NestedConfigurationProperty @Valid Tools tools) {

    public static final String PREFIX = "infobip.openapi.mcp";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    public static final String DEFAULT_USER_AGENT = "openapi-mcp";

    /**
     * Constructor with defaults for optional properties.
     */
    public OpenApiMcpProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
        if (userAgent == null) {
            userAgent = DEFAULT_USER_AGENT;
        }
        if (filters == null) {
            filters = new HashMap<>();
        }
        if (tools == null) {
            tools = new Tools(null, null, null, null, null);
        }
    }

    /**
     * Check if a specific filter is enabled.
     * By default, all filters are enabled if not explicitly configured.
     */
    public boolean isFilterEnabled(String filterName) {
        return filters.getOrDefault(Objects.requireNonNull(filterName), true);
    }

    /**
     * Configuration for tools.
     *
     * @param naming                            Tool naming configuration.
     * @param schema                            Tool schema configuration.
     * @param jsonDoubleSerializationMitigation Whether to enable automatic JSON double serialization mitigation.
     *                                          Default is true.
     * @param prependSummaryToDescription       Whether to prepend the operation summary as a markdown title to the
     *                                          description. Default is true.
     * @param mock                              Whether to run MCP server in mock mode, where it avoids calling API
     *                                          during tool calls and instead returns results based on examples
     *                                          provided in OpenAPI specification. Default is false.
     */
    public record Tools(
            @NestedConfigurationProperty @Valid Naming naming,
            @NestedConfigurationProperty @Valid Schema schema,
            Boolean jsonDoubleSerializationMitigation,
            Boolean prependSummaryToDescription,
            Boolean mock) {
        public static final boolean DEFAULT_JSON_DOUBLE_SERIALIZATION_MITIGATION = true;
        public static final boolean DEFAULT_PREPEND_SUMMARY_TO_DESCRIPTION = true;
        public static final boolean DEFAULT_MOCK = false;

        /**
         * Constructor with defaults for optional properties.
         */
        public Tools {
            if (naming == null) {
                naming = new Naming(null, null);
            }
            if (schema == null) {
                schema = new Schema(null, null);
            }
            if (jsonDoubleSerializationMitigation == null) {
                jsonDoubleSerializationMitigation = DEFAULT_JSON_DOUBLE_SERIALIZATION_MITIGATION;
            }
            if (prependSummaryToDescription == null) {
                prependSummaryToDescription = DEFAULT_PREPEND_SUMMARY_TO_DESCRIPTION;
            }
            if (mock == null) {
                mock = DEFAULT_MOCK;
            }
        }

        /**
         * Configuration for tool naming strategies.
         *
         * @param strategy  The naming strategy to use for generating tool names. Default is SANITIZED_OPERATION_ID.
         * @param maxLength Maximum length for tool names. If specified, names will be trimmed to this length.
         *                  Must be positive if provided.
         */
        public record Naming(
                @NotNull NamingStrategyType strategy,
                @Positive Integer maxLength) {
            private static final NamingStrategyType DEFAULT_STRATEGY = NamingStrategyType.SANITIZED_OPERATION_ID;

            /**
             * Constructor with defaults for optional properties.
             */
            public Naming {
                if (strategy == null) {
                    strategy = DEFAULT_STRATEGY;
                }
            }
        }

        /**
         * Configuration for tool input schema composition.
         *
         * @param parametersKey  The key name used to wrap parameters in combined schemas. Default is "_params".
         * @param requestBodyKey The key name used to wrap request body in combined schemas. Default is "_body".
         */
        public record Schema(
                @NotNull String parametersKey, @NotNull String requestBodyKey) {
            private static final String DEFAULT_PARAMETERS_KEY = "_params";
            private static final String DEFAULT_REQUEST_BODY_KEY = "_body";

            /**
             * Constructor with defaults for optional properties.
             */
            public Schema {
                if (parametersKey == null) {
                    parametersKey = DEFAULT_PARAMETERS_KEY;
                }
                if (requestBodyKey == null) {
                    requestBodyKey = DEFAULT_REQUEST_BODY_KEY;
                }
            }
        }
    }
}
