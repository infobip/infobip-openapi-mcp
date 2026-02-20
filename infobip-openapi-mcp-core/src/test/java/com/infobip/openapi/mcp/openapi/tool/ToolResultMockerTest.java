package com.infobip.openapi.mcp.openapi.tool;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

@NullMarked
class ToolResultMockerTest {

    @Test
    void shouldCallChainIfMockingIsDisabled() {
        // given
        var mocker = new ToolResultMocker(new ObjectMapper(), givenDisabledMockProps());
        var givenCtx = Mockito.mock(McpRequestContext.class);
        var givenReq = Mockito.mock(McpSchema.CallToolRequest.class);
        ToolCallFilterChain givenChain =
                (McpRequestContext ctx, McpSchema.CallToolRequest req) -> new McpSchema.CallToolResult("OK", false);

        // when
        var actualResult = mocker.doFilter(givenCtx, givenReq, givenChain);

        // then
        then(actualResult).isNotNull();
        then(actualResult.isError()).isFalse();
        then(actualResult.content())
                .hasOnlyElementsOfType(McpSchema.TextContent.class)
                .extracting(content -> ((McpSchema.TextContent) content).text())
                .containsExactly("OK");
    }

    public static Stream<Arguments> testDataWithExamples() {
        return Stream.of(
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse(
                                                "200",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse("default", new ApiResponse())
                                        .addApiResponse(
                                                "200",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse("200", new ApiResponse())
                                        .addApiResponse(
                                                "default",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse("200", new ApiResponse())
                                        .addApiResponse(
                                                "2xx",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse("200", new ApiResponse())
                                        .addApiResponse(
                                                "201",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse(
                                                "200",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/xml",
                                                                        new MediaType().example("<request />"))
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(new ApiResponses()
                                        .addApiResponse(
                                                "200",
                                                new ApiResponse()
                                                        .content(new Content()
                                                                .addMediaType(
                                                                        "application/xml",
                                                                        new MediaType().example("<request />"))
                                                                .addMediaType(
                                                                        "application/json; charset=utf-8",
                                                                        new MediaType()
                                                                                .example(Map.of("status", "ok")))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(
                                        new ApiResponses()
                                                .addApiResponse(
                                                        "200",
                                                        new ApiResponse()
                                                                .content(
                                                                        new Content()
                                                                                .addMediaType(
                                                                                        "application/json",
                                                                                        new MediaType()
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "Example name",
                                                                                                                new Example()
                                                                                                                        .value(
                                                                                                                                Map
                                                                                                                                        .of(
                                                                                                                                                "status",
                                                                                                                                                "ok")))))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(
                                        new ApiResponses()
                                                .addApiResponse(
                                                        "200",
                                                        new ApiResponse()
                                                                .content(
                                                                        new Content()
                                                                                .addMediaType(
                                                                                        "application/json",
                                                                                        new MediaType()
                                                                                                .example(
                                                                                                        Map.of(
                                                                                                                "wrong",
                                                                                                                "value"))
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "Example name",
                                                                                                                new Example()
                                                                                                                        .value(
                                                                                                                                Map
                                                                                                                                        .of(
                                                                                                                                                "status",
                                                                                                                                                "ok")))))))),
                        """
                                {"status":"ok"}"""),
                arguments(
                        new Operation()
                                .responses(
                                        new ApiResponses()
                                                .addApiResponse(
                                                        "default",
                                                        new ApiResponse()
                                                                .content(
                                                                        new Content()
                                                                                .addMediaType(
                                                                                        "application/json",
                                                                                        new MediaType()
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "Empty example",
                                                                                                                new Example())))
                                                                                .addMediaType(
                                                                                        "application/xml",
                                                                                        new MediaType()
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "XML example",
                                                                                                                new Example()
                                                                                                                        .value(
                                                                                                                                "<request />"))))))
                                                .addApiResponse(
                                                        "2xx",
                                                        new ApiResponse()
                                                                .content(
                                                                        new Content()
                                                                                .addMediaType(
                                                                                        "application/json",
                                                                                        new MediaType()
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "Empty example",
                                                                                                                new Example())))
                                                                                .addMediaType(
                                                                                        "application/xml",
                                                                                        new MediaType()
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "XML example",
                                                                                                                new Example()
                                                                                                                        .value(
                                                                                                                                "<request />"))))))
                                                .addApiResponse(
                                                        "200",
                                                        new ApiResponse()
                                                                .content(
                                                                        new Content()
                                                                                .addMediaType(
                                                                                        "application/json",
                                                                                        new MediaType()
                                                                                                .examples(
                                                                                                        Map.of(
                                                                                                                "Example name",
                                                                                                                new Example()
                                                                                                                        .value(
                                                                                                                                Map
                                                                                                                                        .of(
                                                                                                                                                "status",
                                                                                                                                                "ok")))))))),
                        """
                                {"status":"ok"}"""));
    }

    @ParameterizedTest
    @MethodSource("testDataWithExamples")
    void shouldReturnExamples(Operation givenOperation, String expectedResContent) {
        // given
        var mocker = new ToolResultMocker(new ObjectMapper(), givenEnabledMockProps());
        var givenCtx = Mockito.mock(McpRequestContext.class);
        given(givenCtx.openApiOperation())
                .willReturn(new FullOperation("/mock/path", PathItem.HttpMethod.GET, givenOperation, new OpenAPI()));
        var givenReq = Mockito.mock(McpSchema.CallToolRequest.class);
        ToolCallFilterChain givenChain = (McpRequestContext ctx, McpSchema.CallToolRequest req) -> {
            throw new IllegalStateException("Should not be called");
        };

        // when
        var actualResult = mocker.doFilter(givenCtx, givenReq, givenChain);

        // then
        then(actualResult).isNotNull();
        then(actualResult.isError()).isFalse();
        then(actualResult.content())
                .hasOnlyElementsOfType(McpSchema.TextContent.class)
                .extracting(content -> ((McpSchema.TextContent) content).text())
                .containsExactly(expectedResContent);
    }

    public static Stream<Arguments> testDataWithoutExamples() {
        return Stream.of(
                arguments(new Operation()),
                arguments(new Operation().responses(new ApiResponses())),
                arguments(new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()))),
                arguments(new Operation()
                        .responses(new ApiResponses()
                                .addApiResponse(
                                        "400",
                                        new ApiResponse()
                                                .content(new Content()
                                                        .addMediaType(
                                                                "application/json",
                                                                new MediaType()
                                                                        .example(Map.of("status", "bad request"))))))),
                arguments(new Operation()
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(new Content())))),
                arguments(new Operation()
                        .responses(new ApiResponses()
                                .addApiResponse(
                                        "200",
                                        new ApiResponse()
                                                .content(new Content()
                                                        .addMediaType(
                                                                "application/xml",
                                                                new MediaType().example("<request />")))))),
                arguments(new Operation()
                        .responses(new ApiResponses()
                                .addApiResponse(
                                        "200",
                                        new ApiResponse()
                                                .content(new Content()
                                                        .addMediaType("application/json", new MediaType()))))),
                arguments(new Operation()
                        .responses(new ApiResponses()
                                .addApiResponse(
                                        "200",
                                        new ApiResponse()
                                                .content(new Content()
                                                        .addMediaType(
                                                                "application/json",
                                                                new MediaType().examples(Map.of())))))));
    }

    @ParameterizedTest
    @MethodSource("testDataWithoutExamples")
    void shouldReturnMcpErrorResultIfNoExamplesAreFound(Operation givenOperation) {
        // given
        var mocker = new ToolResultMocker(new ObjectMapper(), givenEnabledMockProps());
        var givenCtx = Mockito.mock(McpRequestContext.class);
        given(givenCtx.openApiOperation())
                .willReturn(new FullOperation("/mock/path", PathItem.HttpMethod.GET, givenOperation, new OpenAPI()));
        var givenReq = Mockito.mock(McpSchema.CallToolRequest.class);
        ToolCallFilterChain givenChain = (McpRequestContext ctx, McpSchema.CallToolRequest req) -> {
            throw new IllegalStateException("Should not be called");
        };

        // when
        var actualResult = mocker.doFilter(givenCtx, givenReq, givenChain);

        // then
        then(actualResult).isNotNull();
        then(actualResult.isError()).isTrue();
    }

    private OpenApiMcpProperties givenDisabledMockProps() {
        return new OpenApiMcpProperties();
    }

    private OpenApiMcpProperties givenEnabledMockProps() {
        var tools = new OpenApiMcpProperties.Tools(null, null, null, null, null, true);
        return new OpenApiMcpProperties(null, null, null, null, null, null, tools, null);
    }
}
