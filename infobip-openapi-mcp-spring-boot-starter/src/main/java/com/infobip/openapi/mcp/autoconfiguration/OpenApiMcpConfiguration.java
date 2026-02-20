package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.TOOL_HANDLER_REST_CLIENT_QUALIFIER;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.config.ApiBaseUrlConfig;
import com.infobip.openapi.mcp.config.ApiBaseUrlProvider;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.enricher.*;
import com.infobip.openapi.mcp.error.DefaultErrorModelProvider;
import com.infobip.openapi.mcp.error.ErrorModelProvider;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.MicrometerMetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.*;
import com.infobip.openapi.mcp.openapi.filter.DiscriminatorFlattener;
import com.infobip.openapi.mcp.openapi.filter.OpenApiFilter;
import com.infobip.openapi.mcp.openapi.filter.OpenApiFilterChain;
import com.infobip.openapi.mcp.openapi.filter.PatternPropertyRemover;
import com.infobip.openapi.mcp.openapi.schema.InputSchemaComposer;
import com.infobip.openapi.mcp.openapi.tool.*;
import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategy;
import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategyFactory;
import com.infobip.openapi.mcp.util.OpenApiMapperFactory;
import com.infobip.openapi.mcp.util.ToolSpecBuilder;
import com.infobip.openapi.mcp.util.XForwardedForCalculator;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.server.*;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@AutoConfigureBefore(McpServerAutoConfiguration.class)
@EnableConfigurationProperties(OpenApiMcpProperties.class)
class OpenApiMcpConfiguration {

    @Bean
    public OpenAPIV3Parser openApiParser() {
        OpenAPIV3Parser.setEncoding(StandardCharsets.UTF_8.name());
        return new OpenAPIV3Parser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorModelProvider<?> errorModelProvider() {
        return new DefaultErrorModelProvider();
    }

    @Bean
    public XForwardedForCalculator xffCalculator() {
        return new XForwardedForCalculator();
    }

    @Bean
    public XForwardedHostCalculator xForwardedHostCalculator(
            Optional<McpServerStreamableHttpProperties> streamableProps, Optional<McpServerSseProperties> sseProps) {
        return new XForwardedHostCalculator(streamableProps, sseProps);
    }

    @Bean
    public InputSchemaComposer inputSchemaComposer(OpenApiMcpProperties properties) {
        return new InputSchemaComposer(properties.tools().schema());
    }

    @Bean
    public ErrorModelWriter jsonErrorModelWriter(ObjectMapper objectMapper, ErrorModelProvider<?> errorModelProvider) {
        return new ErrorModelWriter(objectMapper, errorModelProvider);
    }

    @Bean
    public ApiBaseUrlProvider apiBaseUrlResolver(OpenApiMcpProperties properties, OpenApiRegistry openApiRegistry) {
        var config = ApiBaseUrlConfig.parse(properties.apiBaseUrl());
        return new ApiBaseUrlProvider(config, openApiRegistry);
    }

    @Bean
    @Qualifier(TOOL_HANDLER_REST_CLIENT_QUALIFIER)
    public RestClient toolHandlerRestClient(
            OpenApiMcpProperties properties, OpenApiRegistry openApiRegistry, ApiBaseUrlProvider apiBaseUrlProvider) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        // Resolve the base URL from the loaded OpenAPI spec
        var resolvedBaseUrl = apiBaseUrlProvider.get();

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(resolvedBaseUrl.toString())
                .build();
    }

    @Bean
    public XForwardedForEnricher xForwardedForEnricher(XForwardedForCalculator xForwardedForCalculator) {
        return new XForwardedForEnricher(xForwardedForCalculator);
    }

    @Bean
    public XForwardedHostEnricher xForwardedHostEnricher(XForwardedHostCalculator xForwardedHostCalculator) {
        return new XForwardedHostEnricher(xForwardedHostCalculator);
    }

    @Bean
    public UserAgentEnricher userAgentEnricher(OpenApiMcpProperties properties) {
        return new UserAgentEnricher(properties);
    }

    @Bean
    public ApiRequestEnricherChain apiRequestEnricherChain(List<ApiRequestEnricher> enrichers) {
        return new ApiRequestEnricherChain(enrichers);
    }

    @Bean
    public McpRequestContextFactory mcpRequestContextFactory() {
        return new McpRequestContextFactory();
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public MetricService micrometerMetricService(MeterRegistry meterRegistry, NamingStrategy namingStrategy) {
        return new MicrometerMetricService(meterRegistry, namingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MetricService noOpMetricService() {
        return new NoOpMetricService();
    }

    @Bean
    public ToolResultMocker toolResultMocker(ObjectMapper objectMapper, OpenApiMcpProperties properties) {
        return new ToolResultMocker(objectMapper, properties);
    }

    @Bean
    public ToolHandler toolHandler(
            @Qualifier(TOOL_HANDLER_REST_CLIENT_QUALIFIER) RestClient restClient,
            ErrorModelWriter errorModelWriter,
            OpenApiMcpProperties properties,
            ApiRequestEnricherChain enricherChain,
            MetricService metricService) {
        return new ToolHandler(restClient, errorModelWriter, properties, enricherChain, metricService);
    }

    @Bean
    public ToolRegistry toolRegistry(
            OpenApiRegistry openApiRegistry,
            NamingStrategy namingStrategy,
            InputSchemaComposer inputSchemaComposer,
            ToolHandler toolHandler,
            OpenApiMapperFactory openApiMapperFactory,
            OpenApiMcpProperties properties) {
        return new ToolRegistry(
                openApiRegistry, namingStrategy, inputSchemaComposer, toolHandler, openApiMapperFactory, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "SSE")
    @ConditionalOnProperty(
            prefix = McpServerProperties.CONFIG_PREFIX,
            name = "stdio",
            havingValue = "false",
            matchIfMissing = true)
    public List<McpServerFeatures.SyncToolSpecification> toolSpecificationsSSE(
            ToolRegistry toolRegistry, ToolSpecBuilder toolSpecBuilder) {
        return registerTools(toolRegistry, toolSpecBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "STREAMABLE")
    @ConditionalOnProperty(
            prefix = McpServerProperties.CONFIG_PREFIX,
            name = "stdio",
            havingValue = "false",
            matchIfMissing = true)
    public List<McpServerFeatures.SyncToolSpecification> toolSpecificationsStreamable(
            ToolRegistry toolRegistry, ToolSpecBuilder toolSpecBuilder) {
        return registerTools(toolRegistry, toolSpecBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "STATELESS")
    @ConditionalOnProperty(
            prefix = McpServerProperties.CONFIG_PREFIX,
            name = "stdio",
            havingValue = "false",
            matchIfMissing = true)
    public List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecificationsStateless(
            ToolRegistry toolRegistry, ToolSpecBuilder toolSpecBuilder) {
        return registerStatelessTools(toolRegistry, toolSpecBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "stdio", havingValue = "true")
    public List<McpServerFeatures.SyncToolSpecification> toolSpecificationsStdio(
            ToolRegistry toolRegistry, ToolSpecBuilder toolSpecBuilder) {
        return registerTools(toolRegistry, toolSpecBuilder);
    }

    @Bean
    public DiscriminatorFlattener discriminatorFlattener() {
        return new DiscriminatorFlattener();
    }

    @Bean
    public PatternPropertyRemover patternPropertyRemover() {
        return new PatternPropertyRemover();
    }

    @Bean
    public OpenApiFilterChain openApiFilterChain(List<OpenApiFilter> openApiFilters, OpenApiMcpProperties properties) {
        return new OpenApiFilterChain(openApiFilters, properties);
    }

    @Bean
    public OpenApiReader openApiReader(OpenAPIV3Parser openApiParser) {
        return new OpenApiReader(openApiParser);
    }

    @Bean
    public OpenApiMapperFactory openApiMapperFactory() {
        return new OpenApiMapperFactory();
    }

    @Bean
    public OpenApiResolver openApiResolver(OpenAPIV3Parser openApiParser, OpenApiMapperFactory openApiMapperFactory) {
        return new OpenApiResolver(openApiParser, openApiMapperFactory);
    }

    @Bean
    OpenApiRegistry openApiRegistry(
            OpenApiMcpProperties openApiMcpProperties,
            OpenApiReader openApiReader,
            OpenApiFilterChain openApiFilterChain,
            OpenApiResolver openApiResolver) {
        return new OpenApiRegistry(openApiMcpProperties, openApiReader, openApiFilterChain, openApiResolver);
    }

    @Bean
    public NamingStrategyFactory namingStrategyFactory() {
        return new NamingStrategyFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public NamingStrategy namingStrategy(NamingStrategyFactory factory, OpenApiMcpProperties properties) {
        return factory.create(properties.tools().naming());
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = McpServerProperties.CONFIG_PREFIX,
            name = "type",
            havingValue = "ASYNC",
            matchIfMissing = true)
    public McpAsyncServer mcpAsyncServer() {
        throw new BeanCreationNotAllowedException(
                McpAsyncServer.class.getName(),
                "McpAsyncServer is not supported in this configuration. "
                        + "Please use McpSyncServer for synchronous operations by setting the property '"
                        + McpServerProperties.CONFIG_PREFIX + ".type' to 'SYNC'.");
    }

    @Bean
    public McpServerMetaData mcpServerMetaData(Environment environment, OpenApiRegistry registry) {
        return new McpServerMetaData(environment, registry);
    }

    @Bean
    public OpenApiBasedMcpServerPropertiesCustomizer openApiBasedMcpServerPropertiesRegistry(
            Optional<McpServerProperties> properties, McpServerMetaData metaData) {
        return new OpenApiBasedMcpServerPropertiesCustomizer(properties.orElse(null), metaData);
    }

    @Bean
    public ToolSpecBuilder toolSpecBuilder(List<ToolCallFilter> filters, McpRequestContextFactory contextFactory) {
        return new ToolSpecBuilder(filters, contextFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = OpenApiMcpProperties.LiveReload.PREFIX, name = "enabled", havingValue = "true")
    public OpenApiLiveReload openApiLiveReload(
            Optional<McpSyncServer> mcpSyncServer,
            Optional<McpStatelessSyncServer> mcpStatelessSyncServer,
            Optional<ScopeDiscoveryService> scopeDiscoveryService,
            OpenApiRegistry openApiRegistry,
            ToolRegistry toolRegistry,
            ToolSpecBuilder toolSpecBuilder,
            OpenApiMcpProperties properties,
            MetricService metricService) {
        return new OpenApiLiveReload(
                mcpSyncServer,
                mcpStatelessSyncServer,
                scopeDiscoveryService,
                openApiRegistry,
                toolRegistry,
                toolSpecBuilder,
                properties,
                metricService);
    }

    /**
     * Helper method that loads the OpenAPI registry and executes a registration function.
     * This encapsulates the common pattern of loading the registry and transforming tools.
     *
     * @param toolRegistry     the tool registry containing registered tools
     * @param registerFunction the function to transform registered tools into specifications
     * @param <T>              the type of tool specification to return
     * @return a list of tool specifications
     */
    private <T> List<T> registerAfterLoadingOpenApiRegistry(
            ToolRegistry toolRegistry, Function<RegisteredTool, T> registerFunction) {
        return toolRegistry.getTools().stream().map(registerFunction).toList();
    }

    /**
     * Helper method to register tools for non-stateless MCP protocols (SSE, Streamable, Stdio).
     * These tools receive both the server exchange context and the call tool request.
     */
    private List<McpServerFeatures.SyncToolSpecification> registerTools(
            ToolRegistry toolRegistry, ToolSpecBuilder toolSpecBuilder) {
        return registerAfterLoadingOpenApiRegistry(toolRegistry, toolSpecBuilder::buildSyncToolSpecification);
    }

    /**
     * Helper method to register stateless tools for the stateless MCP protocol.
     * These tools receive the transport context and the call tool request.
     */
    private List<McpStatelessServerFeatures.SyncToolSpecification> registerStatelessTools(
            ToolRegistry toolRegistry, ToolSpecBuilder toolSpecBuilder) {
        return registerAfterLoadingOpenApiRegistry(toolRegistry, toolSpecBuilder::buildSyncStatelessToolSpecification);
    }
}
