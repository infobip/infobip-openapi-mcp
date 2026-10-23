package com.infobip.openapi.mcp.auth.web;

import java.net.URI;

/**
 * Endpoints of the authorization server that {@link AuthorizationServerProxy} forwards requests to.
 *
 * @param registration  client registration endpoint (RFC 7591)
 * @param authorization authorization endpoint
 * @param token         token endpoint
 */
public record UpstreamEndpoints(URI registration, URI authorization, URI token) {}
