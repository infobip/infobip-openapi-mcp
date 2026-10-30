package com.infobip.openapi.mcp.autoconfiguration;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Logs requests and responses, including their bodies, of the OAuth endpoints served by the MCP server (the
 * {@code /.well-known} metadata endpoints and the authorization server proxy) at {@code TRACE} level.
 * <p>
 * Values of credentials (the {@code Authorization} header, tokens, client secrets, authorization codes) are replaced
 * with {@value #MASK}, since the logged exchanges carry live credentials.
 * </p>
 */
class OAuthExchangeLogger implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthExchangeLogger.class);

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "access_token",
            "refresh_token",
            "id_token",
            "code",
            "code_verifier",
            "client_secret",
            "client_assertion",
            "assertion",
            "password",
            "registration_access_token");

    private static final Pattern SENSITIVE_QUERY_PARAMETER =
            Pattern.compile("([?&])(" + String.join("|", SENSITIVE_FIELDS) + ")=[^&#]*");

    private final ObjectMapper objectMapper;

    OAuthExchangeLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (!LOGGER.isTraceEnabled()) {
            return next.handle(request);
        }
        var loggedRequest = logRequest(request);
        try {
            var response = next.handle(loggedRequest);
            logResponse(request, response);
            return response;
        } catch (Exception e) {
            LOGGER.trace("OAuth request {} {} failed: {}", request.method(), request.path(), e.toString());
            throw e;
        }
    }

    /**
     * Form parameters are logged from the parameter map, which doesn't consume the request body. Other bodies are read
     * once and handed to the handler through a rebuilt request, since the stream can't be read twice.
     */
    private ServerRequest logRequest(ServerRequest request) throws Exception {
        var servletRequest = request.servletRequest();
        var contentType = request.headers().contentType();
        var formRequest = contentType
                .map(type -> type.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .orElse(false);
        var hasBody = contentType.isPresent() && !formRequest && servletRequest.getContentLengthLong() != 0;

        var loggedRequest = request;
        String body = null;
        if (hasBody) {
            var bytes = request.body(byte[].class);
            body = maskedBody(new String(bytes, UTF_8));
            loggedRequest = ServerRequest.from(request).body(bytes).build();
        }
        LOGGER.trace(
                "OAuth request: {} {} headers={} parameters={} body={}",
                request.method(),
                request.path(),
                maskedHeaders(request.headers().asHttpHeaders()),
                maskedParameters(servletRequest.getParameterMap()),
                body);
        return loggedRequest;
    }

    private void logResponse(ServerRequest request, ServerResponse response) {
        var body = response instanceof EntityResponse<?> entityResponse ? entityBody(entityResponse.entity()) : null;
        LOGGER.trace(
                "OAuth response: {} for {} {} headers={} body={}",
                response.statusCode().value(),
                request.method(),
                request.path(),
                maskedHeaders(response.headers()),
                body);
    }

    private String entityBody(Object entity) {
        try {
            return switch (entity) {
                case byte[] bytes -> maskedBody(new String(bytes, UTF_8));
                case CharSequence text -> maskedBody(text.toString());
                default -> maskedBody(objectMapper.writeValueAsString(entity));
            };
        } catch (JacksonException e) {
            return "<unserializable " + entity.getClass().getSimpleName() + ">";
        }
    }

    private String maskedBody(String body) {
        try {
            var json = objectMapper.readTree(body);
            mask(json);
            return json.toString();
        } catch (JacksonException e) {
            return body;
        }
    }

    private static void mask(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            for (var property : List.copyOf(objectNode.properties())) {
                if (SENSITIVE_FIELDS.contains(property.getKey())
                        && property.getValue().isString()) {
                    objectNode.put(property.getKey(), MASK);
                } else {
                    mask(property.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(OAuthExchangeLogger::mask);
        }
    }

    private static Map<String, List<String>> maskedHeaders(HttpHeaders headers) {
        var masked = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, values) -> masked.put(
                name,
                SENSITIVE_HEADERS.contains(name.toLowerCase())
                        ? List.of(MASK)
                        : values.stream()
                                .map(value -> HttpHeaders.LOCATION.equalsIgnoreCase(name)
                                        ? maskedQueryParameters(value)
                                        : value)
                                .toList()));
        return masked;
    }

    /**
     * Authorization responses are redirects that carry the authorization code in the query of the {@code Location}
     * header.
     */
    private static String maskedQueryParameters(String uri) {
        return SENSITIVE_QUERY_PARAMETER.matcher(uri).replaceAll("$1$2=" + MASK);
    }

    private static Map<String, List<String>> maskedParameters(Map<String, String[]> parameters) {
        var masked = new LinkedHashMap<String, List<String>>();
        parameters.forEach((name, values) ->
                masked.put(name, SENSITIVE_FIELDS.contains(name) ? List.of(MASK) : Arrays.asList(values)));
        return masked;
    }
}
