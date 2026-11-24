package com.infobip.openapi.mcp.auth;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiAuthFailureTest {

    @Test
    void shouldMarkAndReadUnauthorizedStatus() {
        var request = new MockHttpServletRequest();

        ApiAuthFailure.mark(request, HttpStatusCode.valueOf(401));

        then(ApiAuthFailure.read(request)).isEqualTo(HttpStatusCode.valueOf(401));
    }

    @Test
    void shouldMarkAndReadForbiddenStatus() {
        var request = new MockHttpServletRequest();

        ApiAuthFailure.mark(request, HttpStatusCode.valueOf(403));

        then(ApiAuthFailure.read(request)).isEqualTo(HttpStatusCode.valueOf(403));
    }

    @Test
    void shouldIgnoreNonAuthorizationStatuses() {
        var request = new MockHttpServletRequest();

        ApiAuthFailure.mark(request, HttpStatusCode.valueOf(500));
        ApiAuthFailure.mark(request, HttpStatusCode.valueOf(200));
        ApiAuthFailure.mark(request, null);

        then(request.getAttribute(ApiAuthFailure.REQUEST_ATTRIBUTE)).isNull();
        then(ApiAuthFailure.read(request)).isNull();
    }

    @Test
    void shouldNotMarkWhenUnmarked() {
        then(ApiAuthFailure.read(new MockHttpServletRequest())).isNull();
    }

    @Test
    void shouldNotReadAttributeOfUnexpectedType() {
        var request = new MockHttpServletRequest();
        request.setAttribute(ApiAuthFailure.REQUEST_ATTRIBUTE, "401");

        then(ApiAuthFailure.read(request)).isNull();
    }

    @Test
    void shouldKeepFirstMarkWhenMarkedAgainWithNonAuthorizationStatus() {
        var request = new MockHttpServletRequest();
        ApiAuthFailure.mark(request, HttpStatusCode.valueOf(401));

        ApiAuthFailure.mark(request, HttpStatusCode.valueOf(503));

        then(ApiAuthFailure.read(request)).isEqualTo(HttpStatusCode.valueOf(401));
    }
}
