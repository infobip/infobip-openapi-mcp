package com.infobip.openapi.mcp.auth.web;

/**
 * Constants of the OAuth endpoints exposed by {@link AuthorizationServerProxy} and of the OAuth protocol names it
 * handles, grouped by where they appear in the exchange.
 */
public final class Spec {

    private Spec() {}

    /**
     * HTTP paths of the proxied endpoints, relative to the MCP endpoint.
     */
    public static final class Paths {

        private Paths() {}

        public static final String REGISTER = "/oauth/register";
        public static final String AUTHORIZE = "/oauth/authorize";
        public static final String TOKEN = "/oauth/token";
        public static final String CALLBACK = "/oauth/callback";
    }

    /**
     * Paths of the {@code /.well-known} metadata endpoints, served by the MCP server and read from the authorization
     * server.
     */
    public static final class WellKnownPaths {

        private WellKnownPaths() {}

        public static final String OAUTH_AUTHORIZATION_SERVER = "/.well-known/oauth-authorization-server";
        public static final String OPENID_CONFIGURATION = "/.well-known/openid-configuration";
        public static final String OAUTH_PROTECTED_RESOURCE = "/.well-known/oauth-protected-resource";
    }

    /**
     * Properties of the JSON authorization server metadata (<a href="https://www.rfc-editor.org/rfc/rfc8414">RFC
     * 8414</a>).
     */
    public static final class ServerMetadata {

        private ServerMetadata() {}

        public static final String ISSUER = "issuer";
        public static final String REGISTRATION_ENDPOINT = "registration_endpoint";
        public static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
        public static final String TOKEN_ENDPOINT = "token_endpoint";
        public static final String PUSHED_AUTHORIZATION_REQUEST_ENDPOINT = "pushed_authorization_request_endpoint";
        public static final String REQUIRE_PUSHED_AUTHORIZATION_REQUESTS = "require_pushed_authorization_requests";
        public static final String MTLS_ENDPOINT_ALIASES = "mtls_endpoint_aliases";
        public static final String SCOPES_SUPPORTED = "scopes_supported";
    }

    /**
     * Properties of the JSON body of client registration requests and responses
     * (<a href="https://www.rfc-editor.org/rfc/rfc7591">RFC 7591</a>).
     */
    public static final class ClientMetadata {

        private ClientMetadata() {}

        public static final String SCOPE = "scope";
        public static final String REDIRECT_URIS = "redirect_uris";
    }

    /**
     * Query and form parameters of authorization requests and responses, and of token requests.
     */
    public static final class Parameters {

        private Parameters() {}

        public static final String REDIRECT_URI = "redirect_uri";
        public static final String GRANT_TYPE = "grant_type";
        public static final String ISS = "iss";
    }

    /**
     * Values of the {@link Parameters#GRANT_TYPE} parameter.
     */
    public static final class GrantTypes {

        private GrantTypes() {}

        public static final String AUTHORIZATION_CODE = "authorization_code";
    }

    /**
     * Properties of the JSON body of token responses.
     */
    public static final class TokenResponse {

        private TokenResponse() {}

        public static final String ID_TOKEN = "id_token";
    }
}
