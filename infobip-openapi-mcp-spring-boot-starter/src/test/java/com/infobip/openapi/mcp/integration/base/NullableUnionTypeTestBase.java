package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class NullableUnionTypeTestBase extends IntegrationTestBase {

    @Test
    void shouldCallToolWithArrayArgumentWhenParameterSchemaUsesNullableUnionType() {
        withInitializedMcpClient(givenClient -> {
            // Given a 3.1 spec whose "metrics" parameter has type ["array", "null"]
            givenOpenAPISpecification("/openapi/nullable-union-type.json");
            var givenApiResponse = """
                    {"result": "ok"}""";
            getStaticWireMockServer()
                    .stubFor(get(urlPathEqualTo("/traffic"))
                            .willReturn(aResponse().withStatus(200).withBody(givenApiResponse)));

            // When calling the tool with an actual array value
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_traffic_performance_metrics")
                    .arguments(Map.of("metrics", List.of("SENT", "DELIVERED")))
                    .build());

            // Then the MCP SDK validates the array argument successfully and the call succeeds
            then(actualResponse.isError()).isFalse();
            then(actualResponse.content().getFirst())
                    .isInstanceOf(McpSchema.TextContent.class)
                    .extracting(content -> ((McpSchema.TextContent) content).text())
                    .isEqualTo(givenApiResponse);
        });
    }
}
