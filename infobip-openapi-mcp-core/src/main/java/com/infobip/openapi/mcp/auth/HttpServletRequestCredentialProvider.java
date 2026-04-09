package com.infobip.openapi.mcp.auth;

import com.infobip.openapi.mcp.McpRequestContext;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * Default {@link CredentialProvider} that reads the {@code Authorization} header
 * from the incoming HTTP request.
 *
 * <p>Returns {@link Optional#empty()} when:
 * <ul>
 *   <li>the request context has no HTTP request (e.g. stdio transport), or</li>
 *   <li>the {@code Authorization} header is absent or blank.</li>
 * </ul>
 *
 * <p>This bean is registered with {@code @ConditionalOnMissingBean}, so library consumers
 * can replace it by declaring their own {@link CredentialProvider} bean.
 */
public class HttpServletRequestCredentialProvider implements CredentialProvider {

    @Override
    public Optional<String> provide(McpRequestContext context) {
        return Optional.ofNullable(context.httpServletRequest())
                .map(request -> request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(header -> !header.isBlank());
    }
}
