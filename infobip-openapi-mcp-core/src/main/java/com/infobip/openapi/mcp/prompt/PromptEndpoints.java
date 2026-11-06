package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.openapi.schema.Spec;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds the operations of the OpenAPI spec that back backend-resolved prompts.
 * <p>
 * Prompts are declared in the root {@value Spec#MCP_PROMPTS_EXTENSION} extension and linked to an operation only by
 * the {@code resolve.path} string, so the lookup works on the raw extension value. It is deliberately lenient:
 * validation of the extension, and failing startup on invalid definitions, is the job of {@link PromptRegistry}.
 */
public final class PromptEndpoints {

    private PromptEndpoints() {}

    /**
     * Collects the relative {@code resolve.path} values declared in the root {@value Spec#MCP_PROMPTS_EXTENSION}
     * extension. Absolute URLs target a different server and are not included. Entries that are malformed are
     * skipped.
     *
     * @param openApi the OpenAPI specification
     * @return an immutable set of paths, empty when the extension is absent
     */
    public static Set<String> relativeResolvePaths(OpenAPI openApi) {
        var extensions = openApi.getExtensions();
        if (extensions == null || !(extensions.get(Spec.MCP_PROMPTS_EXTENSION) instanceof Collection<?> prompts)) {
            return Set.of();
        }

        var paths = new HashSet<String>();
        for (var prompt : prompts) {
            if (prompt instanceof Map<?, ?> promptMap
                    && promptMap.get("resolve") instanceof Map<?, ?> resolve
                    && resolve.get("path") instanceof String path
                    && path.startsWith("/")) {
                paths.add(path);
            }
        }
        return Set.copyOf(paths);
    }
}
