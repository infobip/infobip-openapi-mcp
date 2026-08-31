package com.infobip.openapi.mcp.openapi.filter;

import com.infobip.openapi.mcp.util.SchemaWalker;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NullableTypeNormalizer normalizes OpenAPI 3.1 nullable union types into a single scalar type.
 * <p>
 * OpenAPI 3.1 aligns with JSON Schema and represents a nullable value as a union of types that
 * includes {@code "null"}, for example {@code {"type": ["array", "null"]}}. Many commonly used MCP
 * client libraries (and the JSON Schema validator used by the MCP Java SDK) cannot handle an
 * array-valued {@code type} keyword and fail with coercion errors when validating tool input.
 * <p>
 * This filter removes the {@code "null"} member from every schema whose {@code type} is a union,
 * collapsing {@code ["array", "null"]} to {@code "array"}. When more than one non-null type remains
 * (an uncommon case such as {@code ["string", "integer", "null"]}), the first non-null type is kept
 * and the rest are dropped with a warning, since a single scalar type is the only form the MCP
 * validators reliably accept.
 * <p>
 * Dropping the {@code "null"} option does not meaningfully impact LLM tool-input generation: the
 * value can simply be omitted when not needed, and requiring the model to reason about explicit
 * {@code null} versus absence adds little value.
 *
 * @see <a href="https://json-schema.org/understanding-json-schema/reference/type">type keyword in JSON Schema</a>
 */
@NullMarked
public class NullableTypeNormalizer implements OpenApiFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NullableTypeNormalizer.class);

    private static final String NULL_TYPE = "null";

    /**
     * @param openApi the OpenAPI specification that potentially contains nullable union types
     * @return the OpenAPI specification with nullable union types collapsed to a single scalar type
     */
    @Override
    public OpenAPI filter(OpenAPI openApi) {
        new SchemaWalker(NullableTypeNormalizer::normalizeTypes).walk(openApi);
        return openApi;
    }

    private static void normalizeTypes(Schema<?> schema) {
        var types = schema.getTypes();
        if (types == null || types.size() <= 1 || !types.contains(NULL_TYPE)) {
            return;
        }

        var nonNullTypes = new LinkedHashSet<>(types);
        nonNullTypes.remove(NULL_TYPE);

        if (nonNullTypes.size() > 1) {
            var retainedType = nonNullTypes.iterator().next();
            LOGGER.warn(
                    "Schema declares multiple non-null types {}. Keeping '{}' and dropping the rest,"
                            + " as MCP clients require a single scalar type.",
                    nonNullTypes,
                    retainedType);
            schema.setTypes(new LinkedHashSet<>(Set.of(retainedType)));
        } else {
            schema.setTypes(nonNullTypes);
        }
    }
}
