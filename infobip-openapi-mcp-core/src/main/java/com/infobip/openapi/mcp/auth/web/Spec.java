package com.infobip.openapi.mcp.auth.web;

/**
 * Constants of the OAuth endpoints exposed by {@link AuthorizationServerProxy}, relative to the MCP endpoint, and of
 * the OAuth protocol parameters it handles.
 */
public final class Spec {

    private Spec() {}

    public static final String REGISTER_PATH = "/oauth/register";
    public static final String AUTHORIZE_PATH = "/oauth/authorize";
    public static final String TOKEN_PATH = "/oauth/token";
    public static final String CALLBACK_PATH = "/oauth/callback";

    public static final String SCOPE = "scope";
    public static final String REDIRECT_URIS = "redirect_uris";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String GRANT_TYPE = "grant_type";
    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String ISS = "iss";
    public static final String ID_TOKEN = "id_token";
}
