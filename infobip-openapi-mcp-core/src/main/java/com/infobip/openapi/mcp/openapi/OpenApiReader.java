package com.infobip.openapi.mcp.openapi;

import com.infobip.openapi.mcp.openapi.exception.InvalidOpenApiException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.net.URI;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public class OpenApiReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiReader.class);

    private final OpenAPIV3Parser parser;

    public OpenApiReader(OpenAPIV3Parser parser) {
        this.parser = parser;
    }

    public OpenAPI read(URI uri) {
        var parseResult = parser.readLocation(uri.toString(), null, configureParseOptions());

        if (parseResult.getOpenAPI() == null) {
            if (parseResult.getMessages() != null) {
                LOGGER.error(
                        "Parsing OpenAPI spec resulted in the following errors: {}.",
                        String.join("; ", parseResult.getMessages()));
            }
            throw InvalidOpenApiException.becauseOfErrorsWhileParsing(uri, parseResult.getMessages());
        }

        if (parseResult.getMessages() != null && !parseResult.getMessages().isEmpty()) {
            LOGGER.warn(
                    "Parsing OpenAPI spec resulted in the following warnings: {}.",
                    String.join("; ", parseResult.getMessages()));
        }

        return parseResult.getOpenAPI();
    }

    private ParseOptions configureParseOptions() {
        var parseOptions = new ParseOptions();
        parseOptions.setResolve(false);
        parseOptions.setResolveFully(false);
        parseOptions.setResolveRequestBody(false);
        parseOptions.setResolveResponses(false);
        parseOptions.setResolveCombinators(false);
        return parseOptions;
    }
}
