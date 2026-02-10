package com.infobip.openapi.mcp.openapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.openapi.schema.InputSchemaComposer;
import com.infobip.openapi.mcp.openapi.tool.RegisteredTool;
import com.infobip.openapi.mcp.openapi.tool.ToolHandler;
import com.infobip.openapi.mcp.openapi.tool.ToolRegistry;
import com.infobip.openapi.mcp.openapi.tool.naming.OperationIdStrategy;
import com.infobip.openapi.mcp.util.OpenApiMapperFactory;
import com.infobip.openapi.mcp.util.ToolSpecBuilder;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.net.URI;
import java.util.Optional;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenApiLiveReloadTest {

    private static final String BASE_SPEC = "/openapi/live-reload/base.json";
    private static final String WITH_ADDED_TOOL_SPEC = "/openapi/live-reload/with-added-tool.json";
    private static final String WITH_DELETED_TOOL_SPEC = "/openapi/live-reload/with-deleted-tool.json";
    private static final String WITH_EDITED_TITLE_SPEC = "/openapi/live-reload/with-edited-title.json";
    private static final String WITH_EDITED_DESCRIPTION_SPEC = "/openapi/live-reload/with-edited-description.json";
    private static final String WITH_EDITED_SCHEMA_SPEC = "/openapi/live-reload/with-edited-schema.json";
    private static final String MULTIPLE_TOOLS_SPEC = "/openapi/live-reload/multiple-tools.json";
    private static final String MULTIPLE_TOOLS_EDITED_SPEC = "/openapi/live-reload/multiple-tools-edited.json";

    private static final OpenApiMcpProperties PROPERTIES = new OpenApiMcpProperties(
            URI.create("file:///dummy"),
            null,
            null,
            null,
            null,
            null,
            new OpenApiMcpProperties.Tools(null, null, null, true, null),
            new OpenApiMcpProperties.LiveReload(true, "0 */1 * * * *", 1));

    @Mock
    private McpSyncServer givenMcpSyncServer;

    @Mock
    private ToolSpecBuilder toolSpecBuilder;

    @Mock
    private ToolHandler toolHandler;

    @Mock
    private OpenApiRegistry givenOpenApiRegistry;

    @Mock
    private MetricService metricService;

    @Mock
    private MetricService.LiveReloadTimer liveReloadTimer;

    @Captor
    private ArgumentCaptor<McpServerFeatures.SyncToolSpecification> syncToolSpecCaptor;

    @Captor
    private ArgumentCaptor<String> toolNameCaptor;

    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();
    private final OpenApiMapperFactory mapperFactory = new OpenApiMapperFactory();
    private final OperationIdStrategy namingStrategy = new OperationIdStrategy();
    private final InputSchemaComposer inputSchemaComposer =
            new InputSchemaComposer(new OpenApiMcpProperties.Tools.Schema(null, null));

    private ToolRegistry givenToolRegistry;

    @BeforeEach
    void setUp() {
        givenToolRegistry = new ToolRegistry(
                givenOpenApiRegistry, namingStrategy, inputSchemaComposer, toolHandler, mapperFactory, PROPERTIES);
        given(metricService.startLiveReloadTimer()).willReturn(liveReloadTimer);
    }

    @Nested
    class ToolAdded {

        @Test
        void shouldRegisterNewToolWhenToolIsAdded() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);
            var givenEditedOpenApi = loadOpenApi(WITH_ADDED_TOOL_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenEditedOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should().addTool(syncToolSpecCaptor.capture());
            then(givenMcpSyncServer).should(never()).removeTool(any());

            var capturedToolSpec = syncToolSpecCaptor.getValue();
            BDDAssertions.then(capturedToolSpec.tool().name()).isEqualTo("createUser");
        }

        @Test
        void shouldRegisterMultipleNewToolsWhenMultipleToolsAreAdded() throws InterruptedException {
            // Given
            var givenEmptyOpenApi = loadOpenApi(WITH_DELETED_TOOL_SPEC);
            var givenMultipleToolsOpenApi = loadOpenApi(MULTIPLE_TOOLS_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenEmptyOpenApi)
                    .willReturn(givenEmptyOpenApi)
                    .willReturn(givenMultipleToolsOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should(times(4)).addTool(syncToolSpecCaptor.capture());
            then(givenMcpSyncServer).should(never()).removeTool(any());

            var capturedToolSpecs = syncToolSpecCaptor.getAllValues();
            BDDAssertions.then(capturedToolSpecs).hasSize(4);
            BDDAssertions.then(capturedToolSpecs)
                    .extracting(spec -> spec.tool().name())
                    .containsExactlyInAnyOrder("getUsers", "getProducts", "getOrders", "getItems");
        }
    }

    @Nested
    class ToolDeleted {

        @Test
        void shouldRemoveToolWhenToolIsDeleted() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);
            var givenEmptyOpenApi = loadOpenApi(WITH_DELETED_TOOL_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenEmptyOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should().removeTool(toolNameCaptor.capture());
            then(givenMcpSyncServer).should(never()).addTool(any());

            BDDAssertions.then(toolNameCaptor.getValue()).isEqualTo("getUsers");
        }

        @Test
        void shouldRemoveMultipleToolsWhenMultipleToolsAreDeleted() throws InterruptedException {
            // Given
            var givenMultipleToolsOpenApi = loadOpenApi(MULTIPLE_TOOLS_SPEC);
            var givenEmptyOpenApi = loadOpenApi(WITH_DELETED_TOOL_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenMultipleToolsOpenApi)
                    .willReturn(givenMultipleToolsOpenApi)
                    .willReturn(givenEmptyOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should(times(4)).removeTool(toolNameCaptor.capture());
            then(givenMcpSyncServer).should(never()).addTool(any());

            var capturedToolNames = toolNameCaptor.getAllValues();
            BDDAssertions.then(capturedToolNames)
                    .containsExactlyInAnyOrder("getUsers", "getProducts", "getOrders", "getItems");
        }
    }

    @Nested
    class ToolEdited {

        @Test
        void shouldDetectChangeWhenToolTitleChanges() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);
            var givenEditedOpenApi = loadOpenApi(WITH_EDITED_TITLE_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenEditedOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should().addTool(syncToolSpecCaptor.capture());
            then(givenMcpSyncServer).should(never()).removeTool(any());

            BDDAssertions.then(syncToolSpecCaptor.getValue().tool().title()).isEqualTo("List All Users");
        }

        @Test
        void shouldDetectChangeWhenToolDescriptionChanges() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);
            var givenEditedOpenApi = loadOpenApi(WITH_EDITED_DESCRIPTION_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenEditedOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should().addTool(syncToolSpecCaptor.capture());
            then(givenMcpSyncServer).should(never()).removeTool(any());

            BDDAssertions.then(syncToolSpecCaptor.getValue().tool().description())
                    .isEqualTo("# List Users\n\nGet all users from the database");
        }

        @Test
        void shouldDetectChangeWhenToolInputSchemaChanges() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);
            var givenEditedOpenApi = loadOpenApi(WITH_EDITED_SCHEMA_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenEditedOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should().addTool(syncToolSpecCaptor.capture());
            then(givenMcpSyncServer).should(never()).removeTool(any());

            var inputSchema = syncToolSpecCaptor.getValue().tool().inputSchema();
            BDDAssertions.then(inputSchema.properties()).containsKeys("limit", "offset");
        }

        @Test
        void shouldDetectMultipleChangesWhenDifferentPropertiesChangeOnDifferentTools() throws InterruptedException {
            // Given
            var givenMultipleToolsOpenApi = loadOpenApi(MULTIPLE_TOOLS_SPEC);
            var givenEditedMultipleToolsOpenApi = loadOpenApi(MULTIPLE_TOOLS_EDITED_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenMultipleToolsOpenApi)
                    .willReturn(givenMultipleToolsOpenApi)
                    .willReturn(givenEditedMultipleToolsOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should(times(4)).addTool(syncToolSpecCaptor.capture());
            then(givenMcpSyncServer).should(never()).removeTool(any());

            var capturedToolSpecs = syncToolSpecCaptor.getAllValues();
            BDDAssertions.then(capturedToolSpecs).hasSize(4);

            var getUsersSpec = capturedToolSpecs.stream()
                    .filter(s -> s.tool().name().equals("getUsers"))
                    .findFirst()
                    .orElseThrow();
            BDDAssertions.then(getUsersSpec.tool().title()).isEqualTo("List All Users");

            var getProductsSpec = capturedToolSpecs.stream()
                    .filter(s -> s.tool().name().equals("getProducts"))
                    .findFirst()
                    .orElseThrow();
            BDDAssertions.then(getProductsSpec.tool().description())
                    .isEqualTo("# List Products\n\nGet all products from catalog");

            var getOrdersSpec = capturedToolSpecs.stream()
                    .filter(s -> s.tool().name().equals("getOrders"))
                    .findFirst()
                    .orElseThrow();
            BDDAssertions.then(getOrdersSpec.tool().inputSchema().properties()).containsKeys("status", "customerId");

            var getItemsSpec = capturedToolSpecs.stream()
                    .filter(s -> s.tool().name().equals("getItems"))
                    .findFirst()
                    .orElseThrow();
            BDDAssertions.then(getItemsSpec.tool().title()).isEqualTo("List All Items");
            BDDAssertions.then(getItemsSpec.tool().description())
                    .isEqualTo("# List All Items\n\nGet all items from inventory");
            BDDAssertions.then(getItemsSpec.tool().inputSchema().properties()).containsKeys("type", "inStock");
        }
    }

    @Nested
    class NoChanges {

        @Test
        void shouldNotReloadToolsWhenVersionDoesNotChange() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should(never()).addTool(any());
            then(givenMcpSyncServer).should(never()).removeTool(any());
            then(givenMcpSyncServer).should(never()).notifyToolsListChanged();
        }

        @Test
        void shouldNotNotifyClientsWhenToolsAreIdentical() throws InterruptedException {
            // Given
            var givenBaseOpenApi = loadOpenApi(BASE_SPEC);
            var givenSameToolsDifferentVersion = loadOpenApi(BASE_SPEC);
            givenSameToolsDifferentVersion.getInfo().setVersion("1.0.1");

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenBaseOpenApi)
                    .willReturn(givenSameToolsDifferentVersion);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should(never()).addTool(any());
            then(givenMcpSyncServer).should(never()).removeTool(any());
            then(givenMcpSyncServer).should(never()).notifyToolsListChanged();
        }
    }

    @Nested
    class MixedChanges {

        @Test
        void shouldHandleAddedDeletedAndEditedToolsSimultaneously() throws InterruptedException {
            // Given
            var givenMultipleToolsOpenApi = loadOpenApi(MULTIPLE_TOOLS_SPEC);
            var givenCustomOpenApi = loadOpenApi(MULTIPLE_TOOLS_SPEC);
            givenCustomOpenApi.getInfo().setVersion("1.0.2");
            givenCustomOpenApi.getPaths().remove("/users");
            givenCustomOpenApi.getPaths().get("/products").getGet().setSummary("List All Products");
            var postOperation = new io.swagger.v3.oas.models.Operation();
            postOperation.setOperationId("createOrder");
            postOperation.setSummary("Create Order");
            postOperation.setDescription("Create a new order");
            givenCustomOpenApi.getPaths().get("/orders").setPost(postOperation);

            given(givenOpenApiRegistry.openApi())
                    .willReturn(givenMultipleToolsOpenApi)
                    .willReturn(givenMultipleToolsOpenApi)
                    .willReturn(givenCustomOpenApi);

            givenToolRegistry.getTools();
            var givenOpenApiLiveReload = givenOpenApiLiveReload();
            setupToolSpecBuilderForNewTools();

            // When
            givenOpenApiLiveReload.refreshOpenApiOnSchedule();

            // Then
            then(givenOpenApiRegistry).should().reloadWithUpdateCheck();
            then(givenMcpSyncServer).should().removeTool("getUsers");
            then(givenMcpSyncServer).should(times(2)).addTool(syncToolSpecCaptor.capture());

            var capturedToolSpecs = syncToolSpecCaptor.getAllValues();
            BDDAssertions.then(capturedToolSpecs)
                    .extracting(spec -> spec.tool().name())
                    .containsExactlyInAnyOrder("getProducts", "createOrder");
        }
    }

    private void setupToolSpecBuilderForNewTools() {
        given(toolSpecBuilder.buildSyncToolSpecification(any())).willAnswer(invocation -> {
            var registeredTool = invocation.getArgument(0, RegisteredTool.class);
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(registeredTool.tool())
                    .callHandler((exchange, request) -> null)
                    .build();
        });
    }

    private String getResourceUri(String resourcePath) {
        return getClass().getResource(resourcePath).toString();
    }

    private OpenApiLiveReload givenOpenApiLiveReload() {
        return new OpenApiLiveReload(
                Optional.of(givenMcpSyncServer),
                Optional.empty(),
                givenOpenApiRegistry,
                givenToolRegistry,
                toolSpecBuilder,
                PROPERTIES,
                metricService);
    }

    private OpenAPI loadOpenApi(String resourcePath) {
        return parser.read(getResourceUri(resourcePath));
    }
}
