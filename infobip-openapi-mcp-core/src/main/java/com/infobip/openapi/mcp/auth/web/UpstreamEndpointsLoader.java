package com.infobip.openapi.mcp.auth.web;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import java.net.URI;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves {@link UpstreamEndpoints} from the OpenID configuration of the authorization server.
 */
public final class UpstreamEndpointsLoader {

    private static final String OPENID_CONFIGURATION_PATH = "/.well-known/openid-configuration";
    private static final String REGISTRATION_ENDPOINT = "registration_endpoint";
    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";

    private UpstreamEndpointsLoader() {}

    /**
     * Reads the OpenID configuration at {@code <oauth.url>/.well-known/openid-configuration}.
     *
     * @param metadataRestClient client used to read the authorization server metadata
     * @param oAuthProperties    OAuth configuration properties
     * @param objectMapper       mapper used to parse the metadata
     * @return the resolved endpoints
     * @throws IllegalStateException if the metadata can't be read or lacks a required endpoint
     */
    public static UpstreamEndpoints load(
            RestClient metadataRestClient, OAuthProperties oAuthProperties, ObjectMapper objectMapper) {
        var metadata = readMetadata(metadataRestClient, oAuthProperties, objectMapper);
        return new UpstreamEndpoints(
                endpoint(metadata, REGISTRATION_ENDPOINT),
                endpoint(metadata, AUTHORIZATION_ENDPOINT),
                endpoint(metadata, TOKEN_ENDPOINT));
    }

    private static JsonNode readMetadata(
            RestClient metadataRestClient, OAuthProperties oAuthProperties, ObjectMapper objectMapper) {
        var openIdConfigurationUri = UriComponentsBuilder.fromUri(oAuthProperties.url())
                .path(OPENID_CONFIGURATION_PATH)
                .build()
                .toUri();
        try {
            var metadataBody = metadataRestClient
                    .get()
                    .uri(openIdConfigurationUri)
                    .retrieve()
                    .body(String.class);
            if (metadataBody == null) {
                throw new IllegalStateException(
                        "Authorization server metadata at " + openIdConfigurationUri + " is empty");
            }
            return objectMapper.readTree(metadataBody);
        } catch (RestClientException | JacksonException e) {
            throw new IllegalStateException(
                    "Failed to read authorization server metadata at " + openIdConfigurationUri, e);
        }
    }

    private static URI endpoint(JsonNode metadata, String name) {
        var endpoint = metadata.path(name).asString(null);
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Authorization server metadata has no " + name);
        }
        return URI.create(endpoint);
    }
}
