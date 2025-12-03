package com.infobip.openapi.mcp.openapi.tool.naming;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SanitizedOperationIdStrategyTest {

    private final SanitizedOperationIdStrategy strategy = new SanitizedOperationIdStrategy();

    @Test
    void shouldThrowExceptionWhenOperationIdIsNull() {
        // Given
        var operation = new Operation(); // no operationId set
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, operation, new OpenAPI());

        // When & Then
        thenThrownBy(() -> strategy.name(fullOperation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation ID is null - cannot determine how to proceed with naming.");
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "send_sms_message; send_sms_message",
                "getUserById; getuserbyid",
                "Create-User-Profile; create_user_profile",
                "create-user-profile; create_user_profile",
                "GetUserAccountBalance; getuseraccountbalance",
                "Get User Info; get_user_info",
                "Update\tUser  Profile Data; update_user_profile_data",
                "___Delete_User_Account; delete_user_account",
                "UpdateUserProfile___; updateuserprofile",
                "Get__User___Profile____Data; get_user_profile_data",
                "__Create-User  Profile&&Settings!!__; create_user_profile_settings",
                "CreateApiKey2FAToken; createapikey2fatoken",
                "CreateUserDataÄÖÜ123; createuserdataäöü123",
                "___Send--SMS  Message123!!Bulk___; send_sms_message123_bulk",
                "Send_SMS-Message#Bulk@Users; send_sms_message_bulk_users"
            },
            delimiter = ';')
    void shouldSanitizeOperationIdCorrectly(String input, String expected) {
        // Given
        var operation = new Operation().operationId(input);
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, operation, new OpenAPI());

        // When
        var result = strategy.name(fullOperation);

        // Then
        then(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n", "   \t\n  "})
    void shouldThrowExceptionWhenOperationIdIsEmptyOrWhitespace(String operationId) {
        // Given
        var operation = new Operation().operationId(operationId);
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, operation, new OpenAPI());

        // When & Then
        thenThrownBy(() -> strategy.name(fullOperation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        "Operation ID '%s' contains no valid alphanumeric characters - cannot generate a valid name.",
                        operationId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"!@#$%^&*()", "---", "###", "  !@# $%^  ", "()[]{}+-="})
    void shouldThrowExceptionWhenOperationIdContainsNoValidCharacters(String operationId) {
        // Given
        var operation = new Operation().operationId(operationId);
        var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, operation, new OpenAPI());

        // When & Then
        thenThrownBy(() -> strategy.name(fullOperation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains no valid alphanumeric characters - cannot generate a valid name");
    }
}
