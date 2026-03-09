package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

public abstract class ListToolsTestBase extends IntegrationTestBase {

    @Test
    void shouldListBaseTool() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/base.json");

            // When
            var tools = givenClient.listTools().tools();

            // Then
            then(tools).hasSize(1);
            then(tools.getFirst().name()).isEqualTo("get_test");
            then(tools.getFirst().description()).isNullOrEmpty();
        });
    }

    @Test
    void shouldListBaseAndReloadTool() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenFirstOpenApiSpecPath = "/openapi/base.json";
            var givenSecondOpenApiSpecPath = "/openapi/base-extended.json";

            // When
            reloadTools(givenFirstOpenApiSpecPath);
            var firstTools = givenClient.listTools().tools();

            reloadTools(givenSecondOpenApiSpecPath);
            var secondTools = givenClient.listTools().tools();

            // Then
            then(firstTools).hasSize(1);
            then(firstTools.getFirst().name()).isEqualTo("get_test");

            // Then
            then(secondTools).hasSize(2);
            then(secondTools.stream().map(McpSchema.Tool::name)).containsExactlyInAnyOrder("get_test", "post_test");
        });
    }

    @Test
    void shouldListManyTools() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/many-tools.json");

            // When
            var tools = givenClient.listTools().tools();

            // Then
            var actualPatchTool = tools.stream()
                    .filter(tool -> tool.name().equals("patch_test3"))
                    .findFirst()
                    .orElse(null);

            then(tools).hasSize(7);
            then(tools.stream().map(McpSchema.Tool::name))
                    .containsExactlyInAnyOrder(
                            "get_test1",
                            "post_test1",
                            "put_test1",
                            "delete_test1",
                            "head_test2",
                            "patch_test3",
                            "options_test3");
            then(actualPatchTool).isNotNull();
            then(actualPatchTool.description()).isEqualTo("test description");

            // Verify annotations based on HTTP method semantics
            var getTool = findTool(tools, "get_test1");
            then(getTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, true, false, true, true, null));

            var postTool = findTool(tools, "post_test1");
            then(postTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, false, false, false, true, null));

            var putTool = findTool(tools, "put_test1");
            then(putTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, false, false, true, true, null));

            var deleteTool = findTool(tools, "delete_test1");
            then(deleteTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, false, true, true, true, null));

            var headTool = findTool(tools, "head_test2");
            then(headTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, true, false, true, true, null));

            then(actualPatchTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, false, false, false, true, null));

            var optionsTool = findTool(tools, "options_test3");
            then(optionsTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, true, false, true, true, null));
        });
    }

    private McpSchema.Tool findTool(java.util.List<McpSchema.Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }

    @Test
    void shouldOverrideAnnotationsViaVendorExtension() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/annotations-override.json");

            // When
            var tools = givenClient.listTools().tools();

            // Then — POST defaults with vendor extension overrides
            then(tools).hasSize(1);
            var postTool = tools.getFirst();
            then(postTool.name()).isEqualTo("post_test");
            then(postTool.annotations())
                    .usingRecursiveComparison()
                    .isEqualTo(new McpSchema.ToolAnnotations(null, false, false, true, true, true));
        });
    }

    private void reloadTools(String openApiSpecPath) {
        givenOpenAPISpecification(openApiSpecPath);
    }
}
