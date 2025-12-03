package com.infobip.openapi.mcp.openapi.tool.naming;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EndpointStrategyTest {

    private final EndpointStrategy strategy = new EndpointStrategy();

    @ParameterizedTest
    @MethodSource("provideMethodPathTestCases")
    void shouldGenerateCorrectNameFromMethodAndPath(HttpMethod method, String path, String expectedName) {
        // Given
        var fullOperation = new FullOperation(path, method, new Operation(), new OpenAPI());

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo(expectedName);
    }

    static Stream<Arguments> provideMethodPathTestCases() {
        return Stream.of(
                // Basic cases
                arguments(HttpMethod.POST, "/sms/3/messages", "post_sms_3_messages"),
                arguments(HttpMethod.GET, "/users", "get_users"),
                arguments(HttpMethod.PUT, "/api/v1/user", "put_api_v1_user"),
                arguments(HttpMethod.DELETE, "/webhooks", "delete_webhooks"),
                arguments(HttpMethod.PATCH, "/profiles", "patch_profiles"),

                // Path variables
                arguments(HttpMethod.GET, "/users/{id}", "get_users_id"),
                arguments(HttpMethod.PUT, "/users/{userId}/profile", "put_users_userid_profile"),
                arguments(HttpMethod.DELETE, "/webhooks/{webhook-id}/events", "delete_webhooks_webhook_id_events"),
                arguments(
                        HttpMethod.POST,
                        "/api/v1/messages/{messageId}/status",
                        "post_api_v1_messages_messageid_status"),

                // Hyphens and underscores
                arguments(HttpMethod.GET, "/user-profiles", "get_user_profiles"),
                arguments(HttpMethod.POST, "/sms-messages", "post_sms_messages"),
                arguments(HttpMethod.PUT, "/user_settings", "put_user_settings"),
                arguments(HttpMethod.GET, "/some-endpoint/sub_path", "get_some_endpoint_sub_path"),
                arguments(HttpMethod.POST, "/mixed-case_with-hyphens", "post_mixed_case_with_hyphens"),

                // Mixed case paths
                arguments(HttpMethod.GET, "/sms/3/Messages", "get_sms_3_messages"),
                arguments(HttpMethod.POST, "/API/v1/Users", "post_api_v1_users"),
                arguments(HttpMethod.PUT, "/CamelCase/Path", "put_camelcase_path"),

                // Special characters (should be removed)
                arguments(HttpMethod.GET, "/api@v1/users", "get_apiv1_users"),
                arguments(HttpMethod.POST, "/sms.2.text", "post_sms2text"),
                arguments(HttpMethod.PUT, "/users#section", "put_userssection"),
                arguments(HttpMethod.PATCH, "/path+with+plus", "patch_pathwithplus"),

                // Complex path variables with special characters
                arguments(
                        HttpMethod.GET,
                        "/users/{user-id}/messages/{message.id}",
                        "get_users_user_id_messages_messageid"),
                arguments(
                        HttpMethod.POST,
                        "/webhooks/{webhook_id}/events/{event-type}",
                        "post_webhooks_webhook_id_events_event_type"),

                // Edge cases
                arguments(HttpMethod.GET, "/", "get_"),
                arguments(HttpMethod.POST, "/single", "post_single"),
                arguments(HttpMethod.PUT, "///multiple///slashes", "put___multiple___slashes"),
                arguments(HttpMethod.DELETE, "/path/with/many/segments/here", "delete_path_with_many_segments_here"),

                // Numbers and alphanumeric
                arguments(HttpMethod.GET, "/v1/v2/v3", "get_v1_v2_v3"),
                arguments(HttpMethod.POST, "/api2024/users123", "post_api2024_users123"),
                arguments(HttpMethod.PUT, "/path123-with456_numbers", "put_path123_with456_numbers"),

                // Long paths
                arguments(
                        HttpMethod.GET,
                        "/api/v1/users/{userId}/profiles/{profileId}/settings/{settingId}/values",
                        "get_api_v1_users_userid_profiles_profileid_settings_settingid_values"));
    }
}
