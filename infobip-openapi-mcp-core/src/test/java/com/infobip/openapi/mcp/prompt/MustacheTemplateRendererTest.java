package com.infobip.openapi.mcp.prompt;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MustacheTemplateRendererTest {

    private MustacheTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MustacheTemplateRenderer();
    }

    @Test
    void shouldCompileAndRenderTemplate() {
        // Given
        renderer.compileTemplates(
                "greet", List.of(new PromptMessageDefinition(McpSchema.Role.USER, "Hello {{name}}, welcome!")));

        // When
        var result = renderer.render("greet", 0, Map.of("name", "Alice"));

        // Then
        then(result).isEqualTo("Hello Alice, welcome!");
    }

    @Test
    void shouldRenderMissingKeyAsEmptyString() {
        // Given
        renderer.compileTemplates(
                "greet",
                List.of(new PromptMessageDefinition(McpSchema.Role.USER, "Hello {{name}}, your role is {{role}}.")));

        // When
        var result = renderer.render("greet", 0, Map.of("name", "Alice"));

        // Then
        then(result).isEqualTo("Hello Alice, your role is .");
    }

    @Test
    void shouldNotHtmlEscapeSpecialCharacters() {
        // Given
        renderer.compileTemplates(
                "code", List.of(new PromptMessageDefinition(McpSchema.Role.USER, "Value: {{value}}")));

        // When
        var result = renderer.render("code", 0, Map.of("value", "<script>alert('xss')</script> & \"quotes\""));

        // Then
        then(result).isEqualTo("Value: <script>alert('xss')</script> & \"quotes\"");
    }

    @Test
    void shouldRenderMultipleTemplatesForSamePrompt() {
        // Given
        renderer.compileTemplates(
                "chat",
                List.of(
                        new PromptMessageDefinition(McpSchema.Role.USER, "Hello {{name}}"),
                        new PromptMessageDefinition(McpSchema.Role.ASSISTANT, "Hi {{name}}, how can I help?"),
                        new PromptMessageDefinition(McpSchema.Role.USER, "Tell me about {{topic}}")));

        // When / Then
        then(renderer.render("chat", 0, Map.of("name", "Bob"))).isEqualTo("Hello Bob");
        then(renderer.render("chat", 1, Map.of("name", "Bob"))).isEqualTo("Hi Bob, how can I help?");
        then(renderer.render("chat", 2, Map.of("topic", "Java"))).isEqualTo("Tell me about Java");
    }

    @Test
    void shouldClearCompiledTemplates() {
        // Given
        renderer.compileTemplates("greet", List.of(new PromptMessageDefinition(McpSchema.Role.USER, "Hello {{name}}")));
        then(renderer.render("greet", 0, Map.of("name", "Alice"))).isEqualTo("Hello Alice");

        // When
        renderer.clear();

        // Then
        thenThrownBy(() -> renderer.render("greet", 0, Map.of("name", "Alice")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenRenderingNonExistentTemplate() {
        // When / Then
        thenThrownBy(() -> renderer.render("nonexistent", 0, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonexistent_0");
    }

    @Test
    void shouldRenderTemplateWithNoPlaceholders() {
        // Given
        renderer.compileTemplates(
                "static", List.of(new PromptMessageDefinition(McpSchema.Role.USER, "This is a static message.")));

        // When
        var result = renderer.render("static", 0, Map.of());

        // Then
        then(result).isEqualTo("This is a static message.");
    }
}
