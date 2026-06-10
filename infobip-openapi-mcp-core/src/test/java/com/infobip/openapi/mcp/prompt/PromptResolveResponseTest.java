package com.infobip.openapi.mcp.prompt;

import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptResolveResponseTest {

    @Test
    void shouldRejectNullMessages() {
        thenThrownBy(() -> new PromptResolveResponse("test", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one message");
    }

    @Test
    void shouldRejectEmptyMessages() {
        thenThrownBy(() -> new PromptResolveResponse("test", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one message");
    }

    @Test
    void shouldRejectMessageWithNullContent() {
        thenThrownBy(() -> new PromptResolveResponse(
                        "test", List.of(new PromptResolveResponse.PromptResolveMessage(McpSchema.Role.USER, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank content");
    }

    @Test
    void shouldRejectMessageWithBlankContent() {
        thenThrownBy(() -> new PromptResolveResponse(
                        "test", List.of(new PromptResolveResponse.PromptResolveMessage(McpSchema.Role.USER, "  "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank content");
    }

    @Test
    void shouldRejectMessageWithNullRole() {
        thenThrownBy(() -> new PromptResolveResponse(
                        "test", List.of(new PromptResolveResponse.PromptResolveMessage(null, "hello"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");
    }

    @Test
    void shouldAcceptValidResponse() {
        new PromptResolveResponse(
                "test", List.of(new PromptResolveResponse.PromptResolveMessage(McpSchema.Role.USER, "hello")));
    }
}
