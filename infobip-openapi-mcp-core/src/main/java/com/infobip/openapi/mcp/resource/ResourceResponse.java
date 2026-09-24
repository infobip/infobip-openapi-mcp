package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

/**
 * A successful downstream API response to a resource read, passed to {@link ResourceContentsConverter}.
 *
 * @param uri              the concrete resource {@code uri} requested by the MCP client
 * @param resourceName     the name of the resource
 * @param declaredMimeType the {@code mimeType} advertised for the resource, derived from the OpenAPI operation
 * @param fullOperation    the OpenAPI operation backing the resource
 * @param statusCode       the HTTP status code of the response
 * @param headers          the HTTP headers of the response
 * @param body             the raw response body, empty if the response has no body
 */
@NullMarked
public record ResourceResponse(
        String uri,
        String resourceName,
        String declaredMimeType,
        FullOperation fullOperation,
        HttpStatusCode statusCode,
        HttpHeaders headers,
        byte[] body) {}
