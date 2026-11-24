package com.infobip.openapi.mcp.auth;

/**
 * The plane on which a downstream API authorization failure reaches the MCP client. The backing API stays the
 * authorization authority in every partial-authentication mode; the plane only chooses how the client learns
 * that authorization is missing.
 */
public enum ChallengePlane {

    /** HTTP {@code 401}/{@code 403} + {@code WWW-Authenticate}, what Claude-family hosts act on; STATELESS protocol only. */
    HTTP,

    /** {@code isError} result carrying {@code _meta["mcp/www_authenticate"]}, what ChatGPT connectors act on. */
    IN_META
}
