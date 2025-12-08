package com.infobip.openapi.mcp.autoconfiguration;

import static com.infobip.openapi.mcp.autoconfiguration.Qualifiers.TOOL_HANDLER_REST_CLIENT_QUALIFIER;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContextFactory;
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
import com.infobip.openapi.mcp.util.XFFCalculator;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
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
    public XFFCalculator xffCalculator() {
        return new XFFCalculator();
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
    @Qualifier(TOOL_HANDLER_REST_CLIENT_QUALIFIER)
    public RestClient toolHandlerRestClient(OpenApiMcpProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.apiBaseUrl().toString())
                .build();
    }

    @Bean
    public XForwardedForEnricher xForwardedForEnricher(XFFCalculator xffCalculator) {
        return new XForwardedForEnricher(xffCalculator);
    }

    @Bean
    public XForwardedHostEnricher xForwardedHostEnricher() {
        return new XForwardedHostEnricher();
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
            ToolRegistry toolRegistry, McpRequestContextFactory contextFactory, List<ToolCallFilter> filters) {
        return registerTools(toolRegistry, contextFactory, filters);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "STREAMABLE")
    @ConditionalOnProperty(
            prefix = McpServerProperties.CONFIG_PREFIX,
            name = "stdio",
            havingValue = "false",
            matchIfMissing = true)
    public List<McpServerFeatures.SyncToolSpecification> toolSpecificationsStreamable(
            ToolRegistry toolRegistry, McpRequestContextFactory contextFactory, List<ToolCallFilter> filters) {
        return registerTools(toolRegistry, contextFactory, filters);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "STATELESS")
    @ConditionalOnProperty(
            prefix = McpServerProperties.CONFIG_PREFIX,
            name = "stdio",
            havingValue = "false",
            matchIfMissing = true)
    public List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecificationsStateless(
            ToolRegistry toolRegistry, McpRequestContextFactory contextFactory, List<ToolCallFilter> filters) {
        return registerStatelessTools(toolRegistry, contextFactory, filters);
    }

    @Bean
    @ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "stdio", havingValue = "true")
    public List<McpServerFeatures.SyncToolSpecification> toolSpecificationsStdio(
            ToolRegistry toolRegistry, McpRequestContextFactory contextFactory, List<ToolCallFilter> filters) {
        return registerTools(toolRegistry, contextFactory, filters);
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
            ToolRegistry toolRegistry, McpRequestContextFactory contextFactory, List<ToolCallFilter> filters) {
        return registerAfterLoadingOpenApiRegistry(toolRegistry, registeredTool -> {
            var chainFactory = new OrderingToolCallFilterChainFactory(registeredTool, filters);
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(registeredTool.tool())
                    .callHandler((mcpSyncServerExchange, callToolRequest) -> {
                        var context = contextFactory.forStatefulTransport(
                                mcpSyncServerExchange, callToolRequest.name(), registeredTool.fullOperation());
                        return chainFactory.get().doFilter(context, callToolRequest);
                    })
                    .build();
        });
    }

    /**
     * Helper method to register stateless tools for the stateless MCP protocol.
     * These tools receive the transport context and the call tool request.
     */
    private List<McpStatelessServerFeatures.SyncToolSpecification> registerStatelessTools(
            ToolRegistry toolRegistry, McpRequestContextFactory contextFactory, List<ToolCallFilter> filters) {
        return registerAfterLoadingOpenApiRegistry(toolRegistry, registeredTool -> {
            var chainFactory = new OrderingToolCallFilterChainFactory(registeredTool, filters);
            return McpStatelessServerFeatures.SyncToolSpecification.builder()
                    .tool(registeredTool.tool())
                    .callHandler((mcpTransportContext, callToolRequest) -> {
                        var context = contextFactory.forStatelessTransport(
                                mcpTransportContext, callToolRequest.name(), registeredTool.fullOperation());
                        return chainFactory.get().doFilter(context, callToolRequest);
                    })
                    .build();
        });
    }
}
