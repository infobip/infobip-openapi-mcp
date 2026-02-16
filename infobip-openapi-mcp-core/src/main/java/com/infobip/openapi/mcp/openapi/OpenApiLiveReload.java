package com.infobip.openapi.mcp.openapi;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.tool.RegisteredTool;
import com.infobip.openapi.mcp.openapi.tool.ToolRegistry;
import com.infobip.openapi.mcp.util.ToolSpecBuilder;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Handles automatic reloading of the OpenAPI specification at runtime.
 *
 * <p>This component periodically fetches the OpenAPI specification and synchronizes MCP tools
 * with any detected changes. It is designed to support zero-downtime updates in distributed
 * deployments where multiple server instances need to converge on the same tool set.
 *
 * <h2>Scheduling</h2>
 * <p>The reload job is triggered by a cron expression (default: every 10 minutes). The job runs
 * directly on Spring's task scheduler thread and blocks the thread during execution. If the
 * application has other scheduled tasks, consider configuring a larger scheduler thread pool.
 *
 * <h2>Retry Mechanism</h2>
 * <p>To handle eventual consistency scenarios (e.g., multiple deployments converging on the same
 * specification version), each scheduled execution attempts up to {@code maxRetries} reloads.
 * The retry loop terminates early on the first successful reload, whether or not tools changed.
 * Retries only occur when the reload fails (e.g., due to network errors), using exponential
 * backoff between attempts.
 *
 * <h2>Change Detection</h2>
 * <p>Changes are detected by comparing the OpenAPI specification version string. When a version
 * change is found, the framework compares the current tool set with the new specification:
 * <ul>
 *   <li>Tools no longer present in the specification are removed</li>
 *   <li>New tools are added</li>
 *   <li>Modified tools (changed name, description, or schema) are replaced</li>
 * </ul>
 *
 * <h2>Client Notification</h2>
 * <p>After tools are updated, connected MCP clients are notified via
 * {@link McpSyncServer#notifyToolsListChanged()} for stateful servers. Stateless servers do not
 * maintain client connections, so no notification is needed.
 *
 * <h2>SDK Limitations</h2>
 * <p>Due to constraints in the MCP SDK (as of Spring AI 1.1.0), tools cannot be updated in batch.
 * Each tool addition or removal triggers a separate operation on the server. This may result in
 * multiple tool change notifications.
 *
 * <h2>Concurrency</h2>
 * <p>Only one refresh operation can run at a time. If a scheduled execution triggers while a
 * previous refresh is still in progress, the new execution is skipped.
 *
 * @see OpenApiMcpProperties.LiveReload
 * @see OpenApiRegistry
 * @see ToolRegistry
 */
public class OpenApiLiveReload {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiLiveReload.class);

    private enum Status {
        SUCCESS_TOOLS_UPDATED("success_tools_updated"),
        SUCCESS_NO_CHANGE("success_no_change"),
        FAILURE("failure");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private final Optional<McpSyncServer> mcpSyncServer;
    private final Optional<McpStatelessSyncServer> mcpStatelessSyncServer;
    private final OpenApiRegistry openApiRegistry;
    private final ToolRegistry toolRegistry;
    private final ToolSpecBuilder toolSpecBuilder;
    private final OpenApiMcpProperties.LiveReload liveReloadConfig;
    private final MetricService metricService;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    public OpenApiLiveReload(
            Optional<McpSyncServer> mcpSyncServer,
            Optional<McpStatelessSyncServer> mcpStatelessSyncServer,
            OpenApiRegistry openApiRegistry,
            ToolRegistry toolRegistry,
            ToolSpecBuilder toolSpecBuilder,
            OpenApiMcpProperties properties,
            MetricService metricService) {
        this.mcpSyncServer = mcpSyncServer;
        this.mcpStatelessSyncServer = mcpStatelessSyncServer;
        this.openApiRegistry = openApiRegistry;
        this.toolRegistry = toolRegistry;
        this.toolSpecBuilder = toolSpecBuilder;
        this.liveReloadConfig = properties.liveReload();
        this.metricService = metricService;
    }

    @Scheduled(cron = "${infobip.openapi.mcp.live-reload.cron-expression:0 */10 * * * *}")
    public void refreshOpenApiOnSchedule() throws InterruptedException {
        if (!refreshInProgress.compareAndSet(false, true)) {
            LOGGER.info("OpenAPI refresh already in progress, skipping this execution.");
            return;
        }

        var timer = metricService.startLiveReloadTimer();
        var status = Status.FAILURE;

        try {
            LOGGER.info("Refreshing OpenAPI on schedule.");

            var currentVersion = openApiRegistry.openApi().getInfo().getVersion();
            var currentTools = toolRegistry.getRegisteredToolsCache();

            var maxRetries = liveReloadConfig.maxRetries();
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    var toolsUpdated = refreshOpenApi(currentVersion, currentTools);
                    status = toolsUpdated ? Status.SUCCESS_TOOLS_UPDATED : Status.SUCCESS_NO_CHANGE;
                    break;
                } catch (Exception e) {
                    LOGGER.error("Error refreshing OpenAPI (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            var backoff = (long) Math.pow(2, attempt - 1);
                            TimeUnit.SECONDS.sleep(backoff);
                        } catch (InterruptedException ex) {
                            LOGGER.warn("Interrupted while waiting for next OpenAPI refresh attempt.");
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (status == Status.FAILURE) {
                LOGGER.warn("OpenAPI refresh failed after {} attempts.", maxRetries);
            } else {
                LOGGER.info("OpenAPI refreshed successfully.");
            }
        } finally {
            metricService.recordLiveReloadExecution(status.getValue());
            timer.record(status.getValue());
            refreshInProgress.set(false);
        }
    }

    /**
     * Refreshes the OpenAPI specification and updates tools if needed.
     *
     * @param currentVersion the current OpenAPI version
     * @param currentTools   the current list of registered tools
     * @return true if tools were updated, false if no changes detected
     */
    private boolean refreshOpenApi(String currentVersion, List<RegisteredTool> currentTools) {
        openApiRegistry.reload();
        var newVersion = openApiRegistry.openApi().getInfo().getVersion();
        if (currentVersion.equals(newVersion)) {
            return false;
        }

        var registeredTools = toolRegistry.getTools();
        var registeredToolMap = getToolMap(registeredTools);
        var currentToolMap = getToolMap(currentTools);

        // Check if tools changed
        var deletedTools = findDeletedTools(currentToolMap, registeredToolMap);
        var addedOrChangedTools = findAddedOrChangedTools(currentToolMap, registeredTools);
        if (addedOrChangedTools.isEmpty() && deletedTools.isEmpty()) {
            return false;
        }

        // Reload tools
        mcpSyncServer.ifPresent(ignored -> registerStateful(addedOrChangedTools, deletedTools));
        mcpStatelessSyncServer.ifPresent(ignored -> registerStateless(addedOrChangedTools, deletedTools));

        return true;
    }

    private List<RegisteredTool> findDeletedTools(
            Map<String, RegisteredTool> currentToolMap, Map<String, RegisteredTool> registeredToolMap) {
        return currentToolMap.values().stream()
                .filter(currentTool ->
                        !registeredToolMap.containsKey(currentTool.tool().name()))
                .toList();
    }

    private List<RegisteredTool> findAddedOrChangedTools(
            Map<String, RegisteredTool> currentToolMap, List<RegisteredTool> registeredTools) {
        return registeredTools.stream()
                .filter(regTool -> {
                    var registeredTool = regTool.tool();
                    var registeredToolName = registeredTool.name();

                    if (!currentToolMap.containsKey(registeredToolName)) {
                        return true;
                    }

                    // Tools changed if they are NOT the same
                    var currentTool = currentToolMap.get(registeredToolName).tool();
                    return !currentTool.equals(registeredTool);
                })
                .toList();
    }

    private void registerStateful(List<RegisteredTool> addedOrChangedTools, List<RegisteredTool> deletedTools) {
        var server = mcpSyncServer.get();
        deletedTools.forEach(deletedTool -> server.removeTool(deletedTool.tool().name()));
        addedOrChangedTools.forEach(
                changedTool -> server.addTool(toolSpecBuilder.buildSyncToolSpecification(changedTool)));
    }

    private void registerStateless(List<RegisteredTool> addedOrChangedTools, List<RegisteredTool> deletedTools) {
        var server = mcpStatelessSyncServer.get();
        deletedTools.forEach(deletedTool -> server.removeTool(deletedTool.tool().name()));
        addedOrChangedTools.forEach(
                changedTool -> server.addTool(toolSpecBuilder.buildSyncStatelessToolSpecification(changedTool)));
    }

    private Map<String, RegisteredTool> getToolMap(List<RegisteredTool> tools) {
        return tools.stream().collect(Collectors.toMap(tool -> tool.tool().name(), Function.identity()));
    }
}
