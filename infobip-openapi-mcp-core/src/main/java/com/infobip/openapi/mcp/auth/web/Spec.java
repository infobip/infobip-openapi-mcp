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
