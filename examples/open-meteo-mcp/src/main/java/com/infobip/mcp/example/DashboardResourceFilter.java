package com.infobip.mcp.example;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.apps.AppMeta;
import com.infobip.openapi.mcp.resource.ResourceCallFilter;
import com.infobip.openapi.mcp.resource.ResourceCallFilterChain;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Serves the dashboard UI resource from the application's own resources instead of calling the
 * downstream API: reads of {@link McpAppsSpecEnhancer#DASHBOARD_URI} are short-circuited here and
 * all other resource reads continue down the filter chain untouched.
 *
 * <p>The contents must declare the exact MCP Apps media type, so {@link AppMeta#MIME_TYPE} is set
 * explicitly on the returned text contents together with the extension's {@code _meta.ui} metadata.
 */
@Component
public class DashboardResourceFilter implements ResourceCallFilter {

    private final String dashboardHtml;

    public DashboardResourceFilter() {
        try (var html = new ClassPathResource("dashboard.html").getInputStream()) {
            this.dashboardHtml = new String(html.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load dashboard.html from the classpath", exception);
        }
    }

    @Override
    public McpSchema.ReadResourceResult doFilter(
            McpRequestContext ctx, McpSchema.ReadResourceRequest req, ResourceCallFilterChain chain) {
        if (!McpAppsSpecEnhancer.DASHBOARD_URI.equals(req.uri())) {
            return chain.doFilter(ctx, req);
        }
        var contents = new McpSchema.TextResourceContents(
                req.uri(),
                AppMeta.MIME_TYPE,
                dashboardHtml,
                Map.of(AppMeta.UI_META_KEY, Map.of("prefersBorder", true)));
        return new McpSchema.ReadResourceResult(List.of(contents));
    }
}
