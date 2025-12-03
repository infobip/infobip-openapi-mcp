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
        });
    }

    private void reloadTools(String openApiSpecPath) {
        givenOpenAPISpecification(openApiSpecPath);
    }
}
