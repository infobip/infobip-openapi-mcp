package com.infobip.openapi.mcp.openapi;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties.CONFIG_PREFIX;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class OpenApiBasedMcpServerPropertiesCustomizerTest {

    @Mock
    private OpenApiRegistry openApiRegistry;

    @Spy
    private McpServerProperties mcpServerProperties;

    @Spy
    private MockEnvironment environment = new MockEnvironment();

    public static Stream<Arguments> testData() {
        return Stream.of(
                arguments(new OpenAPI(), new McpServerProperties()),
                arguments(new OpenAPI().info(new Info()), new McpServerProperties()),
                arguments(
                        new OpenAPI().info(new Info().title("Given title")),
                        custom(props -> props.setName("Given title"))),
                arguments(new OpenAPI().info(new Info().version("1.2.3")), custom(props -> props.setVersion("1.2.3"))),
                arguments(
                        new OpenAPI().info(new Info().description("Description")),
                        custom(props -> props.setInstructions("Description"))),
                arguments(
                        new OpenAPI().info(new Info().summary("Summary")),
                        custom(props -> props.setInstructions("Summary"))),
                arguments(
                        new OpenAPI()
                                .info(new Info()
                                        .title("Title")
                                        .version("3.2.1")
                                        .summary("Summary")
                                        .description("Description")),
                        custom(props -> {
                            props.setName("Title");
                            props.setVersion("3.2.1");
                            props.setInstructions("Description");
                        })));
    }

    @ParameterizedTest
    @MethodSource("testData")
    void shouldCustomizeMcpServerPropertiesBasedOnOpenApiSpecification(
            OpenAPI givenOpenApi, McpServerProperties expectedProperties) {
        // given
        given(openApiRegistry.openApi()).willReturn(givenOpenApi);
        var customizer = new OpenApiBasedMcpServerPropertiesCustomizer(
                mcpServerProperties, new McpServerMetaData(environment, openApiRegistry));

        // when
        customizer.customizeProperties();

        // then
        then(mcpServerProperties)
                .usingRecursiveComparison()
                .ignoringExpectedNullFields()
                .isEqualTo(expectedProperties);
    }

    @Test
    void shouldNotUpdatePropertiesDefinedInSpringEnvironment() {
        // given
        var givenOpenApi = new OpenAPI()
                .info(new Info()
                        .title("Wrong title")
                        .version("3.2.1-WRONG")
                        .summary("Wrong summary")
                        .description("Wrong description"));
        given(openApiRegistry.openApi()).willReturn(givenOpenApi);
        environment.setProperty("%s.%s".formatted(CONFIG_PREFIX, "name"), "Name");
        environment.setProperty("%s.%s".formatted(CONFIG_PREFIX, "version"), "1.2.3");
        environment.setProperty("%s.%s".formatted(CONFIG_PREFIX, "instructions"), "Instructions");
        var customizer = new OpenApiBasedMcpServerPropertiesCustomizer(
                mcpServerProperties, new McpServerMetaData(environment, openApiRegistry));

        // when
        customizer.customizeProperties();

        // then
        var expectedProperties = new McpServerProperties();
        expectedProperties.setName("Name");
        expectedProperties.setVersion("1.2.3");
        expectedProperties.setInstructions("Instructions");
        then(mcpServerProperties)
                .usingRecursiveComparison()
                .ignoringExpectedNullFields()
                .isEqualTo(expectedProperties);
    }

    @Test
    void shouldTreatUnresolvableEnvironmentKeysAsMissing() {
        // given
        var givenOpenApi =
                new OpenAPI().info(new Info().title("Title").version("3.2.1").description("Description"));
        given(openApiRegistry.openApi()).willReturn(givenOpenApi);
        environment.setProperty("%s.%s".formatted(CONFIG_PREFIX, "name"), "${MISSING_ENV_VAR}");
        environment.setProperty("%s.%s".formatted(CONFIG_PREFIX, "version"), "${MISSING_ENV_VAR}");
        environment.setProperty("%s.%s".formatted(CONFIG_PREFIX, "instructions"), "${MISSING_ENV_VAR}");
        var customizer = new OpenApiBasedMcpServerPropertiesCustomizer(
                mcpServerProperties, new McpServerMetaData(environment, openApiRegistry));

        // when
        customizer.customizeProperties();

        // then
        var expectedProperties = custom(props -> {
            props.setName("Title");
            props.setVersion("3.2.1");
            props.setInstructions("Description");
        });
        then(mcpServerProperties)
                .usingRecursiveComparison()
                .ignoringExpectedNullFields()
                .isEqualTo(expectedProperties);
    }

    private static McpServerProperties custom(Consumer<McpServerProperties> customizer) {
        var props = new McpServerProperties();
        customizer.accept(props);
        return props;
    }
}
