package com.infobip.mcp.example;

import com.infobip.openapi.mcp.openapi.filter.OpenApiFilter;
import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The upstream Open-Meteo specification cannot know about MCP Apps, so the vendor extensions are
 * injected here before the framework builds its tools and resources:
 *
 * <ul>
 *   <li>the forecast operation is linked to the dashboard UI resource with the {@code x-mcp-app}
 *       extension (its successful JSON responses additionally gain {@code structuredContent}), and</li>
 *   <li>an operation marked with {@code x-mcp-app-resource} is added, which makes the server expose
 *       the dashboard as a {@code ui://} MCP resource. Its HTML is not fetched from Open-Meteo but
 *       served by this application itself, see {@link DashboardResourceFilter}.</li>
 * </ul>
 */
@Component
public class McpAppsSpecEnhancer implements OpenApiFilter {

    static final String DASHBOARD_URI = "ui://open-meteo-dashboard";

    @Override
    public OpenAPI filter(OpenAPI openApi) {
        linkForecastToolToDashboard(openApi);
        addDashboardResourceOperation(openApi);
        return openApi;
    }

    private void linkForecastToolToDashboard(OpenAPI openApi) {
        var forecastPath = openApi.getPaths() == null ? null : openApi.getPaths().get("/v1/forecast");
        var forecast = forecastPath == null ? null : forecastPath.getGet();
        if (forecast == null) {
            return;
        }
        var extensions = new LinkedHashMap<String, Object>();
        if (forecast.getExtensions() != null) {
            extensions.putAll(forecast.getExtensions());
        }
        extensions.put(
                Spec.MCP_APP_EXTENSION,
                Map.of("resourceUri", DASHBOARD_URI, "visibility", List.of("model", "app")));
        forecast.extensions(extensions);
    }

    private void addDashboardResourceOperation(OpenAPI openApi) {
        if (openApi.getPaths() == null || openApi.getPaths().containsKey("/dashboard")) {
            return;
        }
        var dashboard = new Operation()
                .operationId("getDashboard")
                .description("Renders the Open-Meteo weather dashboard")
                .extensions(Map.of(
                        Spec.MCP_APP_RESOURCE_EXTENSION,
                        Map.of("uri", DASHBOARD_URI, "prefersBorder", true)))
                .responses(new ApiResponses()
                        .addApiResponse(
                                "200",
                                new ApiResponse()
                                        .description("The dashboard HTML document")
                                        .content(new Content()
                                                .addMediaType(
                                                        "text/html",
                                                        new MediaType().schema(new StringSchema())))));
        openApi.getPaths().addPathItem("/dashboard", new PathItem().get(dashboard));
    }
}
