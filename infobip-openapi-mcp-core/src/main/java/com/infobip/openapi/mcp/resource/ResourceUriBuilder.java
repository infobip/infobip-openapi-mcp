package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.util.UriTemplate;

/**
 * Builds the {@code uri}/{@code uriTemplate} for an OpenAPI operation marked as an MCP resource, and reverses that
 * process by extracting path/query parameter values back out of a concrete resource {@code uri} read by an MCP
 * client.
 */
@NullMarked
public class ResourceUriBuilder {

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
        return template.match(stripSchemeAndQuery(resourceUri));
    }

    /**
     * Extracts query parameter values from a concrete resource {@code uri}.
     */
    public Map<String, String> extractQueryParameters(String resourceUri) {
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

    private List<Parameter> parametersByLocation(FullOperation fullOperation, String in) {
        var parameters = fullOperation.operation().getParameters();
        if (parameters == null) {
            return List.of();
        }
        return parameters.stream()
                .filter(parameter -> in.equals(parameter.getIn()))
                .toList();
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

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
