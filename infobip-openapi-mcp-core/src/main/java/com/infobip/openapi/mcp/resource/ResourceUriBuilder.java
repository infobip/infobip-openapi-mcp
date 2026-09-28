package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;
import org.springframework.web.util.UriUtils;

/**
 * Builds the {@code uri}/{@code uriTemplate} for an OpenAPI operation marked as an MCP resource, and reverses that
 * process by extracting path/query parameter values back out of a concrete resource {@code uri} read by an MCP
 * client.
 */
@NullMarked
public class ResourceUriBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUriBuilder.class);

    private final String uriScheme;

    public ResourceUriBuilder(String uriScheme) {
        this.uriScheme = uriScheme;
    }

    public List<Parameter> pathParameters(FullOperation fullOperation) {
        return parametersByLocation(fullOperation, DecomposedRequestData.ParametersByType.PATH);
    }

    public List<Parameter> queryParameters(FullOperation fullOperation) {
        return parametersByLocation(fullOperation, DecomposedRequestData.ParametersByType.QUERY);
    }

    public boolean isTemplate(FullOperation fullOperation) {
        return !pathParameters(fullOperation).isEmpty()
                || !queryParameters(fullOperation).isEmpty();
    }

    /**
     * Builds the concrete {@code uri} (no parameters) or the RFC 6570 {@code uriTemplate} (path and/or query
     * parameters present) for the given operation.
     */
    public String build(FullOperation fullOperation) {
        var builder = new StringBuilder(uriScheme).append("://").append(stripLeadingSlash(fullOperation.path()));

        var queryParamNames =
                queryParameters(fullOperation).stream().map(Parameter::getName).toList();
        if (!queryParamNames.isEmpty()) {
            builder.append("{?").append(String.join(",", queryParamNames)).append("}");
        }
        return builder.toString();
    }

    /**
     * Extracts path variable values from a concrete resource {@code uri} by matching it against the operation's
     * OpenAPI path template.
     */
    public Map<String, String> matchPathVariables(FullOperation fullOperation, String resourceUri) {
        if (pathParameters(fullOperation).isEmpty()) {
            return Map.of();
        }
        var template = new UriTemplate(fullOperation.path());
        var rawVariables = template.match(stripSchemeAndQuery(resourceUri));
        var result = new LinkedHashMap<String, String>();
        rawVariables.forEach((name, value) -> result.put(name, decode(value)));
        return result;
    }

    /**
     * Extracts query parameter values from a concrete resource {@code uri}, keeping only those declared as query
     * parameters on the operation. Undeclared parameters are dropped with a warning rather than forwarded to the
     * downstream API, so that a client cannot inject arbitrary query parameters into the backend call.
     */
    public Map<String, String> extractQueryParameters(FullOperation fullOperation, String resourceUri) {
        var declared =
                queryParameters(fullOperation).stream().map(Parameter::getName).collect(Collectors.toSet());
        var result = new LinkedHashMap<String, String>();
        parseQueryString(resourceUri).forEach((name, value) -> {
            if (declared.contains(name)) {
                result.put(name, value);
            } else {
                LOGGER.warn(
                        "Dropping undeclared query parameter '{}' from resource read of operation '{}'",
                        name,
                        fullOperation.path());
            }
        });
        return result;
    }

    private Map<String, String> parseQueryString(String resourceUri) {
        var queryStart = resourceUri.indexOf('?');
        if (queryStart < 0) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, String>();
        for (var pair : resourceUri.substring(queryStart + 1).split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            var separatorIdx = pair.indexOf('=');
            if (separatorIdx < 0) {
                result.put(decode(pair), "");
            } else {
                result.put(decode(pair.substring(0, separatorIdx)), decode(pair.substring(separatorIdx + 1)));
            }
        }
        return result;
    }

    /**
     * Collects parameters of the given location, merging the Path Item level parameters inherited by every operation
     * on that path with the operation level ones. An operation level parameter overrides an inherited one with the
     * same {@code name} and {@code in} combination, as required by the OpenAPI specification.
     */
    private List<Parameter> parametersByLocation(FullOperation fullOperation, String in) {
        var merged = new LinkedHashMap<String, Parameter>();
        Stream.concat(pathItemParameters(fullOperation).stream(), operationParameters(fullOperation).stream())
                .filter(parameter -> in.equals(parameter.getIn()))
                .forEach(parameter -> merged.put(parameter.getName(), parameter));
        return List.copyOf(merged.values());
    }

    private List<Parameter> pathItemParameters(FullOperation fullOperation) {
        var paths = fullOperation.openApi().getPaths();
        if (paths == null) {
            return List.of();
        }
        var pathItem = paths.get(fullOperation.path());
        if (pathItem == null || pathItem.getParameters() == null) {
            return List.of();
        }
        return pathItem.getParameters();
    }

    private List<Parameter> operationParameters(FullOperation fullOperation) {
        var parameters = fullOperation.operation().getParameters();
        return parameters == null ? List.of() : parameters;
    }

    private String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String stripSchemeAndQuery(String resourceUri) {
        var withoutQuery =
                resourceUri.indexOf('?') >= 0 ? resourceUri.substring(0, resourceUri.indexOf('?')) : resourceUri;
        var schemeIdx = withoutQuery.indexOf("://");
        var withoutScheme = schemeIdx >= 0 ? withoutQuery.substring(schemeIdx + 3) : withoutQuery;
        return withoutScheme.startsWith("/") ? withoutScheme : "/" + withoutScheme;
    }

    /**
     * Percent-decodes a URI component as defined by RFC 3986. Unlike {@code URLDecoder}, which implements
     * {@code application/x-www-form-urlencoded} decoding, a literal {@code +} is preserved rather than turned into a
     * space.
     */
    private String decode(String value) {
        return UriUtils.decode(value, StandardCharsets.UTF_8);
    }
}
