package com.infobip.openapi.mcp.auth.web;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-auth-disabled")
class AuthenticationDisabledTest extends OpenApiTestBase {

    @Test
    void shouldNotRegisterAuthenticationFilterWhenDisabled() {
        // then
        then(applicationContext.getBeansOfType(FilterRegistrationBean.class).values().stream()
                        .map(FilterRegistrationBean::getFilter)
                        .map(bean -> (Class) bean.getClass())
                        .toList())
                .doesNotContain(InitialAuthenticationFilter.class);
    }

    @Test
    void shouldAllowAccessWithoutAuthentication() {
        var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/list",
                    "id": 1
                }
                """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
