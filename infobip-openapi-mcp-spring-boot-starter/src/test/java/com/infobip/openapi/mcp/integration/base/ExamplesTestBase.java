package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

/**
 * Verifies that request examples from OpenAPI specs are correctly appended to MCP tool
 * descriptions. Each test loads {@code examples.json} — a fixture designed to cover every
 * example-source location in the OpenAPI spec — and asserts on the composed example that
 * appears in the tool description.
 *
 * <p>Concrete test classes activating this base must enable example appending:
 * <pre>
 *   &#64;TestPropertySource(properties =
 *       "infobip.openapi.mcp.tools.examples-mode = ALL")
 * </pre>
 */
public abstract class ExamplesTestBase extends IntegrationTestBase {

    private static final String FIXTURE = "/openapi/examples.json";

    @Test
    void shouldAppendExampleFromParameterExampleField() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "get_by_param_example");

            // Then
            then(tool.description()).isEqualTo("""
                            Tests example extraction from parameter.example (singular raw value)

                            ## Example

                            ```json
                            {
                              "userId" : "user-123",
                              "includeDeleted" : false
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendExampleFromParameterExamplesMap() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "get_by_param_examples_map");

            // Then — first declared entry per param wins: "active" and 20
            then(tool.description()).isEqualTo("""
                            Tests example extraction from parameter.examples map (first entry wins)

                            ## Example

                            ```json
                            {
                              "status" : "active",
                              "limit" : 20
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendExampleFromParameterSchemaExampleFallback() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "get_by_param_schema_example");

            // Then — schema.example is the only source, still produces a flat map
            then(tool.description()).isEqualTo("""
                            Tests example extraction from parameter.schema.example (fallback)

                            ## Example

                            ```json
                            {
                              "phoneNumber" : "41793026727",
                              "countryCode" : "CH"
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendExampleFromBodyMediaTypeExampleField() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_media_type_example");

            // Then — body only: example is inlined directly, no _body/_params wrapper
            then(tool.description()).isEqualTo("""
                            Tests example extraction from mediaType.example (raw value on request body)

                            ## Example

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello from media type example"
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendAllEntriesFromBodyMediaTypeExamplesMap() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_media_type_examples_map");

            // Then — both entries rendered; flash-sms-example includes its description paragraph
            then(tool.description()).isEqualTo("""
                            Tests example extraction from mediaType.examples map (all entries rendered)

                            ## Examples

                            ### Standard SMS

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello from examples map"
                            }
                            ```

                            ### Flash SMS

                            Sends a flash message displayed directly on the recipient screen.

                            ```json
                            {
                              "to" : "41793026728",
                              "text" : "Flash message"
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendExampleFromBodySchemaExampleFallback() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_schema_example");

            // Then
            then(tool.description()).isEqualTo("""
                            Tests example extraction from mediaType.schema.example (fallback for request body)

                            ## Example

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello from schema example"
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendCombinedExampleWithParamsAndBodyWrapperKeys() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_combined_params_and_body");

            // Then — both wrapper keys present with nested contents
            then(tool.description()).isEqualTo("""
                            Tests combined params + body producing the wrapped _params/_body structure

                            ## Example

                            ```json
                            {
                              "_params" : {
                                "userId" : "user-456",
                                "notify" : true
                              },
                              "_body" : {
                                "to" : "41793026727",
                                "text" : "Hello combined"
                              }
                            }
                            ```""");
        });
    }

    @Test
    void shouldProduceOneWrappedEntryPerBodyExampleWhenBothParamsAndMultipleBodyExamples() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_combined_multi_body");

            // Then — params merged into each body example; flash entry includes its description
            then(tool.description()).isEqualTo("""
                            Tests combined params with multiple body examples each producing a wrapped entry

                            ## Examples

                            ### Standard SMS

                            ```json
                            {
                              "_params" : {
                                "userId" : "user-789"
                              },
                              "_body" : {
                                "to" : "41793026727",
                                "text" : "Hello multi"
                              }
                            }
                            ```

                            ### Flash SMS

                            A flash message displayed on screen.

                            ```json
                            {
                              "_params" : {
                                "userId" : "user-789"
                              },
                              "_body" : {
                                "to" : "41793026728",
                                "text" : "Flash multi"
                              }
                            }
                            ```""");
        });
    }

    @Test
    void shouldUseMapKeyAsHeadingWhenExampleSummaryIsAbsent() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_examples_map_no_summary");

            // Then — map keys used as headings instead of the repeated generic "Example"
            then(tool.description()).isEqualTo("""
                            Tests that map keys are used as headings when example summary is absent

                            ## Examples

                            ### sms-request

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello from key fallback"
                            }
                            ```

                            ### flash-request

                            A flash message

                            ```json
                            {
                              "to" : "41793026728",
                              "text" : "Flash from key fallback"
                            }
                            ```""");
        });
    }

    @Test
    void shouldResolveAndAppendExamplesDefinedAsComponentRefs() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_component_ref");

            // Then — $ref pointers are resolved; both component examples are rendered
            then(tool.description()).isEqualTo("""
                            Tests that $ref examples in request body are resolved from components

                            ## Examples

                            ### Standard SMS

                            A plain-text outbound SMS.

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello from component ref"
                            }
                            ```

                            ### Flash SMS

                            ```json
                            {
                              "to" : "41793026728",
                              "text" : "Flash from component ref"
                            }
                            ```""");
        });
    }

    @Test
    void shouldNotAppendExampleBlockWhenNoExamplesPresent() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "get_no_examples");

            // Then — only the operation summary is in the description, no example block
            then(tool.description()).isEqualTo("Tests that null is returned when no examples are present anywhere");
        });
    }

    @Test
    void shouldLoadAllElevenToolsFromFixture() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            var tools = client.listTools().tools();

            // Then
            then(tools).hasSize(11);
            then(tools.stream().map(McpSchema.Tool::name))
                    .containsExactlyInAnyOrder(
                            "get_by_param_example",
                            "get_by_param_examples_map",
                            "get_by_param_schema_example",
                            "post_body_media_type_example",
                            "post_body_media_type_examples_map",
                            "post_body_schema_example",
                            "post_combined_params_and_body",
                            "post_combined_multi_body",
                            "post_body_examples_map_no_summary",
                            "get_no_examples",
                            "post_body_component_ref");
        });
    }

    private McpSchema.Tool findTool(McpSyncClient client, String name) {
        return client.listTools().tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }
}
