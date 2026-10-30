package com.infobip.openapi.mcp.auth.web;

import java.net.URI;
import java.util.function.Predicate;

/**
 * CallbackPolicy implementations check if a callback URI provided by
 * MCP client during dynamic client registration (DCR) part of the OAuth
 * flow is allowed or not. This is used combination with the DCR proxy
 * feature of the framework.
 * <p>
 * You <b>must</b> provide an implementation of this interface as a Spring
 * bean when using the proxy with
 * {@code infobip.openapi.mcp.security.auth.oauth.proxy-enabled=true}.
 * </p>
 * <p>
 * Example implementation that restricts callbacks to HTTPS URIs from example.com domain:
 * <pre>{@code
 *  @Bean CallbackPolicy callbackPolicy() {
 *    return uri -> "https".equals(uri.getScheme()) && "example.com".equalsIgnoreCase(uri.getHost());
 *  }
 * }</pre>
 * Example implementation that logs callback URIs for manual review:
 * <pre>{@code
 *  @Bean
 *  CallbackPolicy callbackPolicy() {
 *    var logger = LoggerFactory.getLogger("DcrCallbackLogger");
 *    return uri -> {
 *      logger.info("Callback URI: {}", uri);
 *      return true;
 *    };
 *  }
 * }</pre>
 * </p>
 *
 * @see com.infobip.openapi.mcp.auth.OAuthProperties#proxyEnabled()
 * @see com.infobip.openapi.mcp.auth.web.AuthorizationServerProxy
 */
public interface CallbackPolicy extends Predicate<URI> {}
