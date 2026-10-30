package com.infobip.openapi.mcp.auth.web;

import java.net.URI;

public class RejectedCallbackException extends RuntimeException {
    private final String rejectedCallbackUri;

    private RejectedCallbackException(String message, String rejectedCallbackUri) {
        super(message);
        this.rejectedCallbackUri = rejectedCallbackUri;
    }

    private RejectedCallbackException(String message, String rejectedCallbackUri, Throwable cause) {
        super(message, cause);
        this.rejectedCallbackUri = rejectedCallbackUri;
    }

    public static RejectedCallbackException becausePolicyFailed(URI rejectedCallbackUri) {
        var message = "OAuth redirect URI `%s` does not conform to callback policy".formatted(rejectedCallbackUri);
        return new RejectedCallbackException(message, rejectedCallbackUri.toString());
    }

    public static RejectedCallbackException becauseUriIsInvalid(String invalidUri, Throwable cause) {
        var message = "OAuth redirect URI `%s` is not a valid URI".formatted(invalidUri);
        return new RejectedCallbackException(message, invalidUri, cause);
    }

    public String getRejectedCallbackUri() {
        return rejectedCallbackUri;
    }
}
