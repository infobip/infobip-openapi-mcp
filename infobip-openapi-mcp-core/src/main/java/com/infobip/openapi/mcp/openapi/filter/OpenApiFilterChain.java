package com.infobip.openapi.mcp.openapi.filter;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.exception.OpenApiFilterException;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A chain of OpenAPI filters that applies each filter in sequence to the provided OpenAPI object.
 * Filters can be enabled or disabled based on the configuration.
 */
@NullMarked
public class OpenApiFilterChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiFilterChain.class);

    private final List<OpenApiFilter> filters;
    private final OpenApiMcpProperties openApiMcpProperties;

    public OpenApiFilterChain(List<OpenApiFilter> filters, OpenApiMcpProperties openApiMcpProperties) {
        this.filters = filters;
        this.openApiMcpProperties = openApiMcpProperties;
    }

    /**
     * Applies all configured filters to the provided OpenAPI object.
     * Each filter is applied in the order they are defined.
     *
     * @param openApi the OpenAPI object to be filtered
     * @return the filtered OpenAPI object
     * @throws OpenApiFilterException if an error occurs while applying any filter
     */
    public OpenAPI filter(OpenAPI openApi) {
        var filteredOpenApi = openApi;
        for (OpenApiFilter filter : filters) {
            try {
                if (!openApiMcpProperties.isFilterEnabled(filter.name())) {
                    LOGGER.info("Skipping OpenAPI filter: {} as it is disabled in the configuration.", filter.name());
                    continue;
                }
                LOGGER.info("Applying OpenAPI filter: {}.", filter.name());
                filteredOpenApi = filter.filter(filteredOpenApi);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Error applying filter: {}."
                                + " Please verify the filter behaviour and fix the errors."
                                + " If you prefer to disable the filter, you can do this by setting the respective configuration {}.filters flag to false.",
                        filter.name(),
                        OpenApiMcpProperties.PREFIX,
                        exception);
                throw OpenApiFilterException.becauseOfErrorsWhileFiltering(filter.name(), exception);
            }
        }

        return filteredOpenApi;
    }
}
