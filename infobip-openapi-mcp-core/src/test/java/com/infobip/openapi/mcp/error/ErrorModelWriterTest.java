package com.infobip.openapi.mcp.error;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class ErrorModelWriterTest {

    @Mock
    private ErrorModelProvider<DefaultErrorModel> errorModelProvider;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private Throwable throwable;

    private ErrorModelWriter errorModelWriter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        errorModelWriter = new ErrorModelWriter(objectMapper, errorModelProvider);
    }

    @Nested
    class SuccessfulSerialization {

        @Test
        void shouldWriteErrorModelAsJsonWithAllParameters() throws JSONException {
            // Given
            HttpStatusCode statusCode = HttpStatus.NOT_FOUND;
            var errorModel = new DefaultErrorModel("Not Found", "Resource not found");
            String expectedJson = """
                {
                  "error": "Not Found",
                  "description": "Resource not found"
                }
                """;

            given(errorModelProvider.provide(statusCode, httpServletRequest, throwable))
                    .willReturn(errorModel);

            // When
            var result = errorModelWriter.writeErrorModelAsJson(statusCode, httpServletRequest, throwable);

            // Then
            JSONAssert.assertEquals(expectedJson, result, JSONCompareMode.STRICT);
            verify(errorModelProvider).provide(statusCode, httpServletRequest, throwable);
        }

        @Test
        void shouldWriteErrorModelAsJsonWithStatusCodeOnly() throws JSONException {
            // Given
            HttpStatusCode statusCode = HttpStatus.UNAUTHORIZED;
            var errorModel = new DefaultErrorModel("Unauthorized", "Authentication required");
            String expectedJson = """
                {
                  "error": "Unauthorized",
                  "description": "Authentication required"
                }
                """;

            given(errorModelProvider.provide(statusCode, null, null)).willReturn(errorModel);

            // When
            var result = errorModelWriter.writeErrorModelAsJson(statusCode);

            // Then
            JSONAssert.assertEquals(expectedJson, result, JSONCompareMode.STRICT);
            verify(errorModelProvider).provide(statusCode, null, null);
        }

        @Test
        void shouldHandleEmptyErrorMessage() throws JSONException {
            // Given
            HttpStatusCode statusCode = HttpStatus.NO_CONTENT;
            var errorModel = new DefaultErrorModel("", "");
            String expectedJson = """
                {
                  "error": "",
                  "description": ""
                }
                """;

            given(errorModelProvider.provide(statusCode, null, null)).willReturn(errorModel);

            // When
            var result = errorModelWriter.writeErrorModelAsJson(statusCode);

            // Then
            JSONAssert.assertEquals(expectedJson, result, JSONCompareMode.STRICT);
            verify(errorModelProvider).provide(statusCode, null, null);
        }

        @Test
        void shouldHandleSpecialCharactersInErrorModel() throws JSONException {
            // Given
            HttpStatusCode statusCode = HttpStatus.BAD_REQUEST;
            var errorModel =
                    new DefaultErrorModel("Bad Request", "Invalid JSON: \"missing quote and special chars: àáâãäåæç");
            String expectedJson = """
                {
                  "error": "Bad Request",
                  "description": "Invalid JSON: \\"missing quote and special chars: àáâãäåæç"
                }
                """;

            given(errorModelProvider.provide(statusCode, null, null)).willReturn(errorModel);

            // When
            var result = errorModelWriter.writeErrorModelAsJson(statusCode);

            // Then
            JSONAssert.assertEquals(expectedJson, result, JSONCompareMode.STRICT);
            verify(errorModelProvider).provide(statusCode, null, null);
        }
    }

    @Nested
    class ErrorHandling {

        @Mock
        private ObjectMapper mockObjectMapper;

        private ErrorModelWriter errorModelWriterWithMockedMapper;

        @BeforeEach
        void setUp() {
            errorModelWriterWithMockedMapper = new ErrorModelWriter(mockObjectMapper, errorModelProvider);
        }

        @Test
        void shouldReturnDefaultErrorJsonWhenSerializationFails() throws Exception {
            // Given
            HttpStatusCode statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
            var errorModel = new DefaultErrorModel("Internal Server Error", "Server error");
            var jsonProcessingException = new JsonProcessingException("Serialization failed") {};

            given(errorModelProvider.provide(statusCode, httpServletRequest, throwable))
                    .willReturn(errorModel);
            given(mockObjectMapper.writeValueAsString(errorModel)).willThrow(jsonProcessingException);

            // When
            var result =
                    errorModelWriterWithMockedMapper.writeErrorModelAsJson(statusCode, httpServletRequest, throwable);

            // Then
            then(result).isEqualTo(DefaultErrorModelProvider.INTERNAL_SERVER_ERROR_JSON_REPRESENTATION);
            verify(errorModelProvider).provide(statusCode, httpServletRequest, throwable);
            verify(mockObjectMapper).writeValueAsString(errorModel);
        }

        @Test
        void shouldReturnDefaultErrorJsonWhenSerializationFailsWithStatusCodeOnly() throws Exception {
            // Given
            HttpStatusCode statusCode = HttpStatus.BAD_REQUEST;
            var errorModel = new DefaultErrorModel("Bad Request", "Invalid request");
            var runtimeException = new RuntimeException("Unexpected error");

            given(errorModelProvider.provide(statusCode, null, null)).willReturn(errorModel);
            given(mockObjectMapper.writeValueAsString(errorModel)).willThrow(runtimeException);

            // When
            var result = errorModelWriterWithMockedMapper.writeErrorModelAsJson(statusCode);

            // Then
            then(result).isEqualTo(DefaultErrorModelProvider.INTERNAL_SERVER_ERROR_JSON_REPRESENTATION);
            verify(errorModelProvider).provide(statusCode, null, null);
            verify(mockObjectMapper).writeValueAsString(errorModel);
        }
    }
}
