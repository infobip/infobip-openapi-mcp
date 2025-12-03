package com.infobip.openapi.mcp.openapi.filter;

import com.infobip.openapi.mcp.util.SchemaWalker;
import io.swagger.v3.oas.models.OpenAPI;
import org.jspecify.annotations.NullMarked;

/**
 * PatternPropertyRemover removes all the pattern validation
 * properties from the OpenAPI specification. This is needed because
 * a lot of commonly used MCP client libraries have issues parsing
 * regex patterns.
 * </p>
 * Note that removal of this validation feature does not significantly
 * impact LLM performance, as it would be uneconomical to require LLMs
 * to deterministically evaluate regex expressions against the tool input
 * parameters it generates.
 *
 * @see <a href="https://json-schema.org/understanding-json-schema/reference/string#regexp">pattern keyword in JSON schema</a>
 */
@NullMarked
public class PatternPropertyRemover implements OpenApiFilter {

    /**
     * @param openApi the OpenAPI specification that potentially contains pattern validation properties
     * @return openAPI specification with pattern properties removed
     */
    @Override
    public OpenAPI filter(OpenAPI openApi) {
        new SchemaWalker(schema -> schema.setPattern(null)).walk(openApi);
        return openApi;
    }
}
