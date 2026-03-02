package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

/**
 * Verifies that in {@code ANNOTATED} examples mode, only examples carrying
 * {@code x-mcp-example: true} are appended to MCP tool descriptions, while
 * unannotated {@code examples} map entries and inline sources ({@code example},
 * {@code schema.example}) are ignored.
 *
 * <p>Concrete test classes activating this base must enable annotated-only example appending:
 * <pre>
 *   &#64;TestPropertySource(properties =
 *       "infobip.openapi.mcp.tools.examples-mode = ANNOTATED")
 * </pre>
 */
public abstract class AnnotatedExamplesTestBase extends IntegrationTestBase {

    private static final String FIXTURE = "/openapi/annotated-examples.json";

    @Test
    void shouldAppendOnlyAnnotatedBodyExample() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_annotated_only");

            // Then — only the annotated entry appears; the unannotated one is excluded
            then(tool.description()).isEqualTo("""
                            Only the annotated body example is appended in ANNOTATED mode

                            ## Examples

                            ### Annotated SMS

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello annotated"
                            }
                            ```""");
        });
    }

    @Test
    void shouldAppendOnlyAnnotatedParameterExample() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "get_param_annotated_only");

            // Then — only the annotated parameter entry; unannotated entry is excluded
            then(tool.description()).isEqualTo("""
                            Only the annotated parameter example is appended in ANNOTATED mode

                            ## Example

                            ```json
                            {
                              "status" : "active"
                            }
                            ```""");
        });
    }

    @Test
    void shouldNotAppendInlineBodyExampleInAnnotatedMode() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_inline_body_ignored");

            // Then — inline mediaType.example and schema.example are both ignored;
            // the inline parameter.example is also excluded because inline sources cannot be annotated
            then(tool.description()).isEqualTo("Inline body example is not appended in ANNOTATED mode");
        });
    }

    @Test
    void shouldAppendOnlyAnnotatedComponentRefExample() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            McpSchema.Tool tool = findTool(client, "post_body_component_ref_annotated");

            // Then — the $ref resolves to the component Example; only the annotated one is included
            then(tool.description()).isEqualTo("""
                            Only the annotated $ref body example is appended in ANNOTATED mode

                            ## Examples

                            ### Annotated component SMS

                            ```json
                            {
                              "to" : "41793026727",
                              "text" : "Hello from annotated component ref"
                            }
                            ```""");
        });
    }

    @Test
    void shouldLoadFourToolsFromFixture() {
        withInitializedMcpClient(client -> {
            // Given
            givenOpenAPISpecification(FIXTURE);

            // When
            var tools = client.listTools().tools();

            // Then
            then(tools).hasSize(4);
            then(tools.stream().map(McpSchema.Tool::name))
                    .containsExactlyInAnyOrder(
                            "post_body_annotated_only",
                            "get_param_annotated_only",
                            "post_inline_body_ignored",
                            "post_body_component_ref_annotated");
        });
    }

    private McpSchema.Tool findTool(McpSyncClient client, String name) {
        return client.listTools().tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }
}
