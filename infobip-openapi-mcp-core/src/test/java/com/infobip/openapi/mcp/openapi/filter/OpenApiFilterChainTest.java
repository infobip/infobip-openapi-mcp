package com.infobip.openapi.mcp.openapi.filter;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.exception.OpenApiFilterException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OpenApiFilterChainTest {

    private final OpenAPI originalOpenApi = new OpenAPI()
            .info(new Info()
                    .title("Original Title")
                    .description("Original Description")
                    .version("1.0.0"));

    private final TitleModifierFilter titleModifierFilter = new TitleModifierFilter();
    private final DescriptionModifierFilter descriptionModifierFilter = new DescriptionModifierFilter();
    private final FaultyFilter faultyFilter = new FaultyFilter();
    private final OrderTestFilter orderTestFilter = new OrderTestFilter();
    private final CustomNameFilter customNameFilter = new CustomNameFilter();

    @Test
    void shouldApplyAllEnabledFiltersInSequence() {
        // Given
        var properties = createPropertiesWithFilters(Map.of(
                "TitleModifierFilter", true,
                "DescriptionModifierFilter", true));

        var filters = List.of(titleModifierFilter, descriptionModifierFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result).isNotNull();
        then(result.getInfo().getTitle()).isEqualTo("Modified: Original Title");
        then(result.getInfo().getDescription()).isEqualTo("Enhanced: Original Description");
    }

    @Test
    void shouldEnableFiltersByDefaultWhenNotSpecified() {
        // Given
        var properties = createPropertiesWithFilters(Map.of());

        var filters = List.of(titleModifierFilter, descriptionModifierFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result).isNotNull();
        then(result.getInfo().getTitle()).isEqualTo("Modified: Original Title");
        then(result.getInfo().getDescription()).isEqualTo("Enhanced: Original Description");
    }

    @Test
    void shouldSkipDisabledFilters() {
        // Given
        var properties = createPropertiesWithFilters(Map.of("TitleModifierFilter", false));

        var filters = List.of(titleModifierFilter, descriptionModifierFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result).isNotNull();
        then(result.getInfo().getTitle()).isEqualTo("Original Title"); // Not modified
        then(result.getInfo().getDescription()).isEqualTo("Enhanced: Original Description"); // Modified
    }

    @Test
    void shouldReturnOriginalOpenAPIWhenNoFiltersProvided() {
        // Given
        var properties = createPropertiesWithFilters(Map.of());
        var filterChain = new OpenApiFilterChain(List.of(), properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result).isSameAs(originalOpenApi);
    }

    @Test
    void shouldReturnOriginalOpenAPIWhenAllFiltersDisabled() {
        // Given
        var properties = createPropertiesWithFilters(Map.of(
                "TitleModifierFilter", false,
                "DescriptionModifierFilter", false));

        var filters = List.of(titleModifierFilter, descriptionModifierFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result).isSameAs(originalOpenApi);
        then(result.getInfo().getTitle()).isEqualTo("Original Title");
        then(result.getInfo().getDescription()).isEqualTo("Original Description");
    }

    @Test
    void shouldThrowOpenAPIFilterExceptionWhenFilterThrowsException() {
        // Given
        var properties = createPropertiesWithFilters(Map.of(
                "TitleModifierFilter", true,
                "FaultyFilter", true));

        var filters = List.of(titleModifierFilter, faultyFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When & Then
        thenThrownBy(() -> filterChain.filter(originalOpenApi))
                .isInstanceOf(OpenApiFilterException.class)
                .hasMessageContaining("OpenAPI spec filtering failed by filter: FaultyFilter");
    }

    @Test
    void shouldApplyFiltersInCorrectOrder() {
        // Given
        var properties = createPropertiesWithFilters(Map.of());

        // This filter adds a prefix, then the title modifier adds another prefix
        var filters = List.of(orderTestFilter, titleModifierFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        // First filter adds "Order: ", then title modifier adds "Modified: "
        then(result.getInfo().getTitle()).isEqualTo("Modified: Order: Original Title");
    }

    @Test
    void shouldContinueProcessingAfterSkippingDisabledFilter() {
        // Given
        var properties = createPropertiesWithFilters(Map.of(
                "TitleModifierFilter", false,
                "DescriptionModifierFilter", true,
                "OrderTestFilter", true));

        var filters = List.of(titleModifierFilter, descriptionModifierFilter, orderTestFilter);
        var filterChain = new OpenApiFilterChain(filters, properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result.getInfo().getTitle()).isEqualTo("Order: Original Title"); // Only OrderTestFilter applied
        then(result.getInfo().getDescription())
                .isEqualTo("Enhanced: Original Description"); // DescriptionModifierFilter applied
    }

    @Test
    void shouldLogFilterExecution(CapturedOutput output) {
        // Given
        var properties = createPropertiesWithFilters(Map.of("TitleModifierFilter", true));

        var filterChain = new OpenApiFilterChain(List.of(titleModifierFilter), properties);

        // When
        filterChain.filter(originalOpenApi);

        // Then
        then(output.getOut()).contains("Applying OpenAPI filter: TitleModifierFilter");
    }

    @Test
    void shouldLogDisabledFilterSkipping(CapturedOutput output) {
        // Given
        var properties = createPropertiesWithFilters(Map.of("TitleModifierFilter", false));

        var filterChain = new OpenApiFilterChain(List.of(titleModifierFilter), properties);

        // When
        filterChain.filter(originalOpenApi);

        // Then
        then(output.getOut())
                .contains("Skipping OpenAPI filter: TitleModifierFilter as it is disabled in the configuration");
    }

    @Test
    void shouldLogErrorWhenFilterThrowsException(CapturedOutput output) {
        // Given
        var properties = createPropertiesWithFilters(Map.of("FaultyFilter", true));

        var filterChain = new OpenApiFilterChain(List.of(faultyFilter), properties);

        // When & Then
        thenThrownBy(() -> filterChain.filter(originalOpenApi)).isInstanceOf(OpenApiFilterException.class);

        then(output.getOut()).contains("Error applying filter: FaultyFilter");
        then(output.getOut()).contains("Please verify the filter behaviour and fix the errors");
    }

    @Test
    void shouldUseCustomFilterName(CapturedOutput output) {
        // Given
        var properties = createPropertiesWithFilters(Map.of("MyCustomFilterName", true));

        var filterChain = new OpenApiFilterChain(List.of(customNameFilter), properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result.getInfo().getTitle()).isEqualTo("Custom Name Title");
        then(output.getOut()).contains("Applying OpenAPI filter: MyCustomFilterName");
    }

    @Test
    void shouldSkipCustomNamedFilterWhenDisabled(CapturedOutput output) {
        // Given
        var properties = createPropertiesWithFilters(Map.of("MyCustomFilterName", false));

        var filterChain = new OpenApiFilterChain(List.of(customNameFilter), properties);

        // When
        var result = filterChain.filter(originalOpenApi);

        // Then
        then(result.getInfo().getTitle()).isEqualTo("Original Title"); // Not modified
        then(output.getOut())
                .contains("Skipping OpenAPI filter: MyCustomFilterName as it is disabled in the configuration");
    }

    private OpenApiMcpProperties createPropertiesWithFilters(Map<String, Boolean> filterConfig) {
        return new OpenApiMcpProperties(null, null, null, null, null, filterConfig, null, null);
    }

    // Test filter implementations

    /**
     * Test filter that modifies the title by adding a prefix
     */
    @NullMarked
    private static class TitleModifierFilter implements OpenApiFilter {
        @Override
        public OpenAPI filter(OpenAPI openApi) {
            if (openApi.getInfo() != null && openApi.getInfo().getTitle() != null) {
                openApi.getInfo().setTitle("Modified: " + openApi.getInfo().getTitle());
            }
            return openApi;
        }
    }

    /**
     * Test filter that modifies the description by adding a prefix
     */
    @NullMarked
    private static class DescriptionModifierFilter implements OpenApiFilter {
        @Override
        public OpenAPI filter(OpenAPI openApi) {
            if (openApi.getInfo() != null && openApi.getInfo().getDescription() != null) {
                openApi.getInfo()
                        .setDescription("Enhanced: " + openApi.getInfo().getDescription());
            }
            return openApi;
        }
    }

    /**
     * Test filter that throws an exception during filtering
     */
    @NullMarked
    private static class FaultyFilter implements OpenApiFilter {
        @Override
        public OpenAPI filter(OpenAPI openApi) {
            throw new IllegalStateException("Something went wrong in filter");
        }
    }

    /**
     * Test filter for order verification
     */
    @NullMarked
    private static class OrderTestFilter implements OpenApiFilter {
        @Override
        public OpenAPI filter(OpenAPI openApi) {
            if (openApi.getInfo() != null && openApi.getInfo().getTitle() != null) {
                openApi.getInfo().setTitle("Order: " + openApi.getInfo().getTitle());
            }
            return openApi;
        }
    }

    /**
     * Test filter that overrides the default name and modifies the title
     */
    @NullMarked
    private static class CustomNameFilter implements OpenApiFilter {
        @Override
        public OpenAPI filter(OpenAPI openApi) {
            if (openApi.getInfo() != null) {
                openApi.getInfo().setTitle("Custom Name Title");
            }
            return openApi;
        }

        @Override
        public String name() {
            return "MyCustomFilterName";
        }
    }
}
