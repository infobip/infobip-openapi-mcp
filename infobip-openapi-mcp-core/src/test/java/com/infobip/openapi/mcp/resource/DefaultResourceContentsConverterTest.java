package com.infobip.openapi.mcp.resource;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class DefaultResourceContentsConverterTest {

    private static final String URI = "api://document";
    private static final byte[] BODY = "content".getBytes(StandardCharsets.UTF_8);

    private final DefaultResourceContentsConverter converter = new DefaultResourceContentsConverter();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # responseContentType              | declaredMimeType | expectedMimeType
            text/plain                         | application/json | text/plain
            text/csv; charset=UTF-8            | application/json | text/csv
            application/json                   | text/plain       | application/json
            application/problem+json           | application/json | application/problem+json
            application/xml                    | application/json | application/xml
            application/atom+xml               | application/json | application/atom+xml
            application/yaml                   | application/json | application/yaml
            application/javascript             | application/json | application/javascript
            application/vnd.acme.config; charset=UTF-8 | application/json | application/vnd.acme.config
            ''                                 | text/markdown    | text/markdown
            ''                                 | application/json | application/json
            """)
    void shouldReturnTextResourceContentsForTextMediaTypes(
            String responseContentType, String declaredMimeType, String expectedMimeType) {
        // Given
        var response = response(responseContentType, declaredMimeType, BODY);

        // When
        var contents = converter.convert(response, new McpRequestContext());

        // Then
        then(contents)
                .usingRecursiveComparison()
                .isEqualTo(new McpSchema.TextResourceContents(URI, expectedMimeType, "content"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # responseContentType              | declaredMimeType | expectedMimeType
            image/png                          | application/json | image/png
            image/svg+xml                      | application/json | image/svg+xml
            application/pdf                    | application/json | application/pdf
            application/octet-stream           | application/json | application/octet-stream
            application/vnd.acme.config        | application/json | application/vnd.acme.config
            ''                                 | image/jpeg       | image/jpeg
            ''                                 | not-a-media-type | not-a-media-type
            """)
    void shouldReturnBase64EncodedBlobResourceContentsForNonTextMediaTypes(
            String responseContentType, String declaredMimeType, String expectedMimeType) {
        // Given
        var response = response(responseContentType, declaredMimeType, BODY);

        // When
        var contents = converter.convert(response, new McpRequestContext());

        // Then
        then(contents)
                .usingRecursiveComparison()
                .isEqualTo(new McpSchema.BlobResourceContents(
                        URI, expectedMimeType, Base64.getEncoder().encodeToString(BODY)));
    }

    @Test
    void shouldDecodeTextUsingCharsetDeclaredByResponse() {
        // Given
        var body = "čćž".getBytes(StandardCharsets.UTF_16);
        var response = response("text/plain; charset=UTF-16", "text/plain", body);

        // When
        var contents = converter.convert(response, new McpRequestContext());

        // Then
        then(contents)
                .usingRecursiveComparison()
                .isEqualTo(new McpSchema.TextResourceContents(URI, "text/plain", "čćž"));
    }

    @Test
    void shouldReturnTextResourceContentsForConfiguredTextMediaTypes() {
        // Given
        var customConverter =
                new DefaultResourceContentsConverter(List.of(MediaType.parseMediaType("application/vnd.acme.config")));
        var response = response("application/vnd.acme.config", "application/json", BODY);

        // When
        var contents = customConverter.convert(response, new McpRequestContext());

        // Then
        then(contents)
                .usingRecursiveComparison()
                .isEqualTo(new McpSchema.TextResourceContents(URI, "application/vnd.acme.config", "content"));
    }

    @Test
    void shouldReturnBlobResourceContentsForMediaTypesNotInConfiguredTextMediaTypes() {
        // Given
        var customConverter = new DefaultResourceContentsConverter(List.of(MediaType.TEXT_PLAIN));
        var response = response("application/json", "application/json", BODY);

        // When
        var contents = customConverter.convert(response, new McpRequestContext());

        // Then
        then(contents)
                .usingRecursiveComparison()
                .isEqualTo(new McpSchema.BlobResourceContents(
                        URI, "application/json", Base64.getEncoder().encodeToString(BODY)));
    }

    private ResourceResponse response(String contentType, String declaredMimeType, byte[] body) {
        var headers = new HttpHeaders();
        if (!contentType.isEmpty()) {
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        var operation = new Operation().operationId("getDocument");
        var openApi = new OpenAPI().path("/document", new PathItem().get(operation));
        return new ResourceResponse(
                URI,
                "getDocument",
                declaredMimeType,
                new FullOperation("/document", PathItem.HttpMethod.GET, operation, openApi),
                HttpStatus.OK,
                headers,
                body);
    }
}
