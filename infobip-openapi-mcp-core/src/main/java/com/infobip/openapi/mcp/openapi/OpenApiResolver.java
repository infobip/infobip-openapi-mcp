package com.infobip.openapi.mcp.openapi;

import com.infobip.openapi.mcp.openapi.exception.InvalidOpenApiException;
import com.infobip.openapi.mcp.util.OpenApiMapperFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves all $ref references in the provided OpenAPI specification.
 * <p>
 * It is essential to resolve references to ensure that all schemas are fully defined since MCP tool
 * definitions do not support $ref references. This class uses the OpenAPIV3Parser to read and resolve
 * references within an OpenAPI document.
 * <p>
 * Note: This implementation may have performance implications due to serialization and deserialization.
 * Future optimizations could involve using a more direct method of resolving references without
 * converting to and from JSON.
 */
@NullMarked
public class OpenApiResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiResolver.class);

    private final OpenAPIV3Parser parser;
    private final OpenApiMapperFactory mapperFactory;

    public OpenApiResolver(OpenAPIV3Parser parser, OpenApiMapperFactory mapperFactory) {
        this.parser = parser;
        this.mapperFactory = mapperFactory;
    }

    /**
     * Resolves all $ref references in the given OpenAPI specification.
     * <p>
     * Note: This implementation may have performance implications due to serialization and deserialization.
     * Future optimizations could involve using a more direct method of resolving references without
     * converting to and from JSON.
     *
     * @param openApi The OpenAPI specification to resolve.
     * @return A new OpenAPI specification with all references resolved.
     * @throws InvalidOpenApiException If there are errors while resolving references.
     */
    public OpenAPI resolve(OpenAPI openApi) {
        // todo: Optimize resolving, use the resolver directly instead of serializing to JSON and back
        // Currently, we are facing issues with the built-in resolver not handling all cases correctly.
        String openApiAsStringAgain;
        try {
            openApiAsStringAgain = mapperFactory.mapper(openApi).writeValueAsString(openApi);
        } catch (Exception exception) {
            LOGGER.error("Failed to serialize OpenAPI spec to JSON.", exception);
            throw InvalidOpenApiException.becauseOfErrorsWhileResolvingReferences(exception);
        }

        var parseResult = parser.readContents(openApiAsStringAgain, null, configureResolveOptions());

        if (parseResult.getOpenAPI() == null) {
            if (parseResult.getMessages() != null) {
                LOGGER.error(
                        "Resolving OpenAPI spec resulted in the following errors: {}.",
                        String.join("; ", parseResult.getMessages()));
            }
            throw InvalidOpenApiException.becauseOfErrorsWhileResolvingReferences(parseResult.getMessages());
        }

        return parseResult.getOpenAPI();
    }

    private ParseOptions configureResolveOptions() {
        var parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);
        parseOptions.setResolveRequestBody(true);
        parseOptions.setResolveResponses(true);
        parseOptions.setResolveCombinators(true);
        return parseOptions;
    }
}
