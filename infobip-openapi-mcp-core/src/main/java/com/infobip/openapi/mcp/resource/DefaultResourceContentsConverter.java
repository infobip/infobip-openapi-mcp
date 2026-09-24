package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/**
 * Default {@link ResourceContentsConverter} that decides between text and blob contents based solely on the media
 * type of the response.
 *
 * <p>The media type is taken from the response {@code Content-Type} header, falling back to the {@code mimeType}
 * declared for the resource. The contents are returned as {@link McpSchema.TextResourceContents} when the media type
 * declares a {@code charset} parameter, or is included in the configured text media types (by default
 * {@link #DEFAULT_TEXT_MEDIA_TYPES}). Text is decoded using the declared charset, UTF-8 by default. Any other media
 * type is returned as base64 encoded {@link McpSchema.BlobResourceContents}, which is lossless for any content.
 *
 * <p>The reported {@code mimeType} is the {@code type/subtype} of the resolved media type, without parameters.
 */
@NullMarked
public class DefaultResourceContentsConverter implements ResourceContentsConverter {

    /**
     * Media types returned as text by default. Wildcards and structured syntax suffixes are supported as defined by
     * {@link MediaType#includes(MediaType)}.
     */
    public static final List<MediaType> DEFAULT_TEXT_MEDIA_TYPES = List.of(
            MediaType.parseMediaType("text/*"),
            MediaType.APPLICATION_JSON,
            MediaType.parseMediaType("application/*+json"),
            MediaType.APPLICATION_XML,
            MediaType.parseMediaType("application/*+xml"),
            MediaType.APPLICATION_YAML,
            MediaType.parseMediaType("application/javascript"));

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResourceContentsConverter.class);

    private final List<MediaType> textMediaTypes;

    public DefaultResourceContentsConverter() {
        this(DEFAULT_TEXT_MEDIA_TYPES);
    }

    /**
     * @param textMediaTypes media types, possibly with wildcards or structured syntax suffixes, whose contents are
     *                       returned as text
     */
    public DefaultResourceContentsConverter(List<MediaType> textMediaTypes) {
        this.textMediaTypes = List.copyOf(textMediaTypes);
    }

    @Override
    public McpSchema.ResourceContents convert(ResourceResponse response, McpRequestContext context) {
        var mediaType = resolveMediaType(response);
        if (mediaType == null) {
            return new McpSchema.BlobResourceContents(
                    response.uri(),
                    response.declaredMimeType(),
                    Base64.getEncoder().encodeToString(response.body()));
        }

        var mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        if (isText(mediaType)) {
            var charset = mediaType.getCharset() != null ? mediaType.getCharset() : StandardCharsets.UTF_8;
            return new McpSchema.TextResourceContents(response.uri(), mimeType, new String(response.body(), charset));
        }
        return new McpSchema.BlobResourceContents(
                response.uri(), mimeType, Base64.getEncoder().encodeToString(response.body()));
    }

    /**
     * Returns whether contents of the given media type are returned as text. Subclasses can override this method to
     * customize the decision.
     */
    protected boolean isText(MediaType mediaType) {
        return mediaType.getCharset() != null
                || textMediaTypes.stream().anyMatch(textMediaType -> textMediaType.includes(mediaType));
    }

    private @Nullable MediaType resolveMediaType(ResourceResponse response) {
        var contentType = response.headers().getContentType();
        if (contentType != null) {
            return contentType;
        }
        try {
            return MediaType.parseMediaType(response.declaredMimeType());
        } catch (InvalidMediaTypeException exception) {
            LOGGER.debug(
                    "Declared MIME type '{}' of resource '{}' is not a valid media type, returning contents as blob",
                    response.declaredMimeType(),
                    response.resourceName());
            return null;
        }
    }
}
