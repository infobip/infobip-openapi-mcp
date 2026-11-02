package com.infobip.openapi.mcp.auth.web;

import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.AUTHORIZATION_ENDPOINT;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.REGISTRATION_ENDPOINT;
import static com.infobip.openapi.mcp.auth.web.Spec.ServerMetadata.TOKEN_ENDPOINT;

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

    private UpstreamEndpointsLoader() {}

    /**
     * Reads the OpenID configuration at {@code <oauth.url>/.well-known/openid-configuration}.
     *
     * @param metadataRestClient client used to read the authorization server metadata; may or may not throw on non-2xx
     *                           responses, both are reported as a failure
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
                .path(Spec.WellKnownPaths.OPENID_CONFIGURATION)
                .build()
                .toUri();
        try {
            var metadataResponse = metadataRestClient
                    .get()
                    .uri(openIdConfigurationUri)
                    .retrieve()
                    .toEntity(String.class);
            if (!metadataResponse.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Authorization server metadata at " + openIdConfigurationUri
                        + " could not be read, status " + metadataResponse.getStatusCode());
            }
            var metadataBody = metadataResponse.getBody();
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
