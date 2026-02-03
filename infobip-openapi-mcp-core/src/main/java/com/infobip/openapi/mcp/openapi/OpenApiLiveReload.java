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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

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

    private static final int VIRTUAL_THREAD_PINNING_FIX_VERSION = 24;

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
    public Thread refreshOpenApiOnSchedule() throws InterruptedException {
        if (!refreshInProgress.compareAndSet(false, true)) {
            LOGGER.info("OpenAPI refresh already in progress, skipping this execution.");
            return null;
        }

        if (liveReloadConfig.threadType() == OpenApiMcpProperties.LiveReload.ThreadType.VIRTUAL_THREADS) {
            return Thread.startVirtualThread(refreshOpenApiOnScheduleTask());
        }

        var thread = new Thread(refreshOpenApiOnScheduleTask());
        thread.start();
        return thread;
    }

    private Runnable refreshOpenApiOnScheduleTask() {
        return () -> {
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
                        if (status == Status.SUCCESS_TOOLS_UPDATED) {
                            break;
                        }
                    } catch (Exception e) {
                        LOGGER.error(
                                "Error refreshing OpenAPI (attempt {}/{}): {}", attempt, maxRetries, e.getMessage(), e);
                        if (attempt < maxRetries) {
                            try {
                                TimeUnit.SECONDS.sleep(1);
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
        };
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

        // Notify clients if the server is stateful
        mcpSyncServer.ifPresent(McpSyncServer::notifyToolsListChanged);

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
