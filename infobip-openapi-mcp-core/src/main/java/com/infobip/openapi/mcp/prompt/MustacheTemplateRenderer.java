package com.infobip.openapi.mcp.prompt;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles and renders Mustache templates for inline mode prompts.
 *
 * <p>HTML escaping is disabled because prompt messages are plain text, not HTML.
 * Templates are compiled once at startup and cached for repeated invocation.
 */
class MustacheTemplateRenderer {

    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory() {
        @Override
        public void encode(String value, Writer writer) {
            try {
                writer.write(value);
            } catch (java.io.IOException e) {
                throw new com.github.mustachejava.MustacheException("Failed to write unescaped value", e);
            }
        }
    };

    private final Map<String, Mustache> compiledTemplates = new ConcurrentHashMap<>();

    /**
     * Compiles all message templates for a given prompt.
     *
     * @param promptName the prompt name used as key prefix
     * @param messages   the inline message definitions containing template content
     */
    void compileTemplates(String promptName, List<PromptMessageDefinition> messages) {
        for (int i = 0; i < messages.size(); i++) {
            var key = templateKey(promptName, i);
            var template =
                    mustacheFactory.compile(new StringReader(messages.get(i).content()), key);
            compiledTemplates.put(key, template);
        }
    }

    /**
     * Renders a previously compiled template with the provided arguments.
     *
     * @param promptName   the prompt name
     * @param messageIndex the zero-based message index
     * @param arguments    the argument values to substitute
     * @return the rendered message content
     */
    String render(String promptName, int messageIndex, Map<String, Object> arguments) {
        var key = templateKey(promptName, messageIndex);
        var template = compiledTemplates.get(key);
        if (template == null) {
            throw new IllegalStateException("No compiled template found for key: " + key);
        }
        var writer = new StringWriter();
        template.execute(writer, arguments);
        return writer.toString();
    }

    /**
     * Removes all compiled templates. Called before live reload recompilation.
     */
    void clear() {
        compiledTemplates.clear();
    }

    private String templateKey(String promptName, int messageIndex) {
        return promptName + "_" + messageIndex;
    }
}
