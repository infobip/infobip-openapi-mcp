package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

public abstract class InitializationTestBase extends IntegrationTestBase {

    @Test
    void shouldExposeServerInfoBasedOnOpenApiSpec() {
        try (var client = mcpSyncClient) {
            var actual = client.initialize();
            var expected = expectedResult();
            then(actual).usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(expected);
        }
    }

    protected abstract McpSchema.InitializeResult expectedResult();
}
