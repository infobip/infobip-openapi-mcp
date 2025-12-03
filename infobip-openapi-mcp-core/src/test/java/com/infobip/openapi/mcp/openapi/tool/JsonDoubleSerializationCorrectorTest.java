package com.infobip.openapi.mcp.openapi.tool;

import static org.assertj.core.api.BDDAssertions.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.MediaType;

class JsonDoubleSerializationCorrectorTest {

    private final JsonDoubleSerializationCorrector corrector = new JsonDoubleSerializationCorrector();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnEmptyWhenNoDoubleSerializationDetected() throws Exception {
        // Given - normal JSON without double serialization
        var inputJson = """
            {
              "message": "Hello World",
              "count": 42,
              "active": true
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty (no correction needed)
        then(result).isEmpty();
    }

    @Test
    void shouldDetectAndCorrectDoubleSerializedJsonObject() throws Exception {
        // Given - JSON with double-serialized object
        var inputJson = """
            {
              "messages": "{\\"destinations\\":[{\\"to\\":\\"some destination\\"}],\\"content\\":{\\"text\\":\\"Hello\\"}}"
            }
            """;

        var expectedOutputJson = """
            {
              "messages": {
                "destinations": [{"to": "some destination"}],
                "content": {"text": "Hello"}
              }
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldDetectAndCorrectDoubleSerializedJsonArray() throws Exception {
        // Given - JSON with double-serialized array
        var inputJson = """
            {
              "messages": "[{\\"destinations\\":[{\\"to\\":\\"destination1\\"}],\\"content\\":{\\"text\\":\\"Hello1\\"}},{\\"destinations\\":[{\\"to\\":\\"destination2\\"}],\\"content\\":{\\"text\\":\\"Hello2\\"}}]"
            }
            """;

        var expectedOutputJson = """
            {
              "messages": [
                {
                  "destinations": [{"to": "destination1"}],
                  "content": {"text": "Hello1"}
                },
                {
                  "destinations": [{"to": "destination2"}],
                  "content": {"text": "Hello2"}
                }
              ]
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldHandleNestedDoubleSerializedJson() throws Exception {
        // Given - JSON with nested double serialization (two levels deep)
        var inputJson = """
            {
              "data": "{\\"nested\\":\\"{\\\\\\"value\\\\\\":\\\\\\"deep\\\\\\"}\\",\\"other\\":\\"value\\"}"
            }
            """;

        var expectedOutputJson = """
            {
              "data": {
                "nested": {
                  "value": "deep"
                },
                "other": "value"
              }
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldNotModifyValidJsonStrings() throws Exception {
        // Given - JSON with legitimate string values that shouldn't be parsed
        var inputJson = """
            {
              "validJsonString": "This is just a string with {quotes} and [brackets]",
              "anotherString": "Not JSON: {incomplete",
              "normalField": "normal value"
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty (no correction needed)
        then(result).isEmpty();
    }

    @Test
    void shouldHandleMixedContentCorrectly() throws Exception {
        // Given - JSON with mixed normal and double-serialized fields
        var inputJson = """
            {
              "normalField": "normal value",
              "numberField": 123,
              "doubleSerializedField": "{\\"nested\\":\\"value\\"}",
              "anotherNormalField": ["item1", "item2"]
            }
            """;

        var expectedOutputJson = """
            {
              "normalField": "normal value",
              "numberField": 123,
              "doubleSerializedField": {
                "nested": "value"
              },
              "anotherNormalField": ["item1", "item2"]
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldHandleComplexDoubleSerializedStructure() throws Exception {
        // Given - the exact example from the Jira issue
        var inputJson = """
            {
              "messages": "[{\\"destinations\\":[{\\"to\\":\\"some destination\\"}],\\"content\\":{\\"text\\":\\"Hello\\"}}]"
            }
            """;

        var expectedOutputJson = """
            {
              "messages": [
                {
                  "destinations": [{"to": "some destination"}],
                  "content": {"text": "Hello"}
                }
              ]
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldHandleArraysWithDoubleSerializedElements() throws Exception {
        // Given - array containing double-serialized elements
        var inputJson = """
            ["{\\"type\\":\\"message1\\"}", "normal string", "{\\"type\\":\\"message2\\"}"]
            """;

        var expectedOutputJson = """
            [
              {"type": "message1"},
              "normal string",
              {"type": "message2"}
            ]
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldHandleEmptyAndWhitespaceStrings() throws Exception {
        // Given - JSON with empty and whitespace strings
        var inputJson = """
            {
              "empty": "",
              "whitespace": "   "
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty (no correction needed)
        then(result).isEmpty();
    }

    @Test
    void shouldHandleMalformedJsonGracefully() throws Exception {
        // Given - JSON with malformed strings that look like JSON
        var inputJson = """
            {
              "malformed1": "{\\"incomplete\\": ",
              "malformed2": "[\\"unclosed array\\"",
              "malformed3": "{\\"invalid\\": json}",
              "validField": "normal value"
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty (malformed JSON should not be modified)
        then(result).isEmpty();
    }

    @Test
    void shouldHandleSpecialCharactersInJson() throws Exception {
        // Given - JSON with special characters that could cause issues
        var inputJson = """
            {
              "data": "{\\"specialChars\\":\\"Special: áéíóú, 中文, emoji: 🚀\\",\\"quotes\\":\\"String with \\\\\\"quotes\\\\\\" inside\\",\\"backslashes\\":\\"Path: C:\\\\\\\\Users\\\\\\\\test\\\\\\\\file.txt\\"}"
            }
            """;

        var expectedOutputJson = """
            {
              "data": {
                "specialChars": "Special: áéíóú, 中文, emoji: 🚀",
                "quotes": "String with \\"quotes\\" inside",
                "backslashes": "Path: C:\\\\Users\\\\test\\\\file.txt"
              }
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldHandleMixedMalformedAndValidDoubleSerializedJson() throws Exception {
        // Given - JSON with both malformed and valid double-serialized content
        var inputJson = """
            {
              "malformedField": "{\\"incomplete\\": ",
              "validDoubleSerializedField": "{\\"nested\\":\\"value\\"}",
              "anotherMalformedField": "[\\"unclosed",
              "normalField": "just a string"
            }
            """;

        var expectedOutputJson = """
            {
              "malformedField": "{\\"incomplete\\": ",
              "validDoubleSerializedField": {
                "nested": "value"
              },
              "anotherMalformedField": "[\\"unclosed",
              "normalField": "just a string"
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should correct only the valid double-serialized field, leave malformed as-is
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldHandleStringPayloadWithDoubleSerializedJson() throws Exception {
        // Given - entire payload is a double-serialized JSON string
        var inputJson = """
            "{\\"message\\":\\"Hello\\",\\"data\\":{\\"count\\":42}}"
            """;

        var expectedOutputJson = """
            {
              "message": "Hello",
              "data": {
                "count": 42
              }
            }
            """;

        var inputPayload = objectMapper.readValue(inputJson, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should unwrap the entire string into proper JSON structure
        then(result).isPresent();
        then(result.get().targetContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var actualOutputJson = objectMapper.writeValueAsString(result.get().content());
        JSONAssert.assertEquals(expectedOutputJson, actualOutputJson, JSONCompareMode.STRICT);
    }

    @Test
    void shouldReturnEmptyWhenNull() {
        // Given - body with null content
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, null);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty gracefully
        then(result).isEmpty();
    }

    @Test
    void shouldHandleInvalidPayloadGracefully() {
        // Given - payload that cannot be processed
        var invalidPayload = new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Simulated processing error");
            }
        };
        var body = new DecomposedRequestData.Body(MediaType.APPLICATION_JSON, invalidPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty gracefully without throwing
        then(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenRequestBodyHasNonJsonMediaType() throws Exception {
        // Given - body with non-JSON media type
        var content = """
            {
              "messages": "{\\"destinations\\":[{\\"to\\":\\"some destination\\"}],\\"content\\":{\\"text\\":\\"Hello\\"}}"
            }
            """;
        var inputPayload = objectMapper.readValue(content, Object.class);
        var body = new DecomposedRequestData.Body(MediaType.TEXT_PLAIN, inputPayload);

        // When
        var result = corrector.correctIfDetected(body);

        // Then - should return empty because media type is not JSON-compatible
        then(result).isEmpty();
    }
}
