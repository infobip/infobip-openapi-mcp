package com.infobip.openapi.mcp.openapi.schema;

import static com.infobip.openapi.mcp.openapi.schema.Spec.MCP_EXAMPLE_EXTENSION;
import static com.infobip.openapi.mcp.openapi.schema.Spec.SUPPORTED_PARAMETER_TYPES;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.schema.Spec.ExamplesMode;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Composes representative request examples for an OpenAPI operation by extracting
 * examples from parameters and request body, then combining them using the same
 * structure as {@link InputSchemaComposer}.
 * <p>
 * When the request body exposes multiple named examples (via the {@code examples} map),
 * each entry produces its own {@link ComposedExample} — merged with the common parameter
 * examples — so that all variants are surfaced in the tool description.  Inline
 * ({@code example} / {@code schema.example}) body values always yield a single entry.
 * <p>
 * The composed example follows these combination rules:
 * <ul>
 *   <li><b>Only parameters declared, param examples present</b> - flat object {@code {"paramName": value, ...}}</li>
 *   <li><b>Only body declared, body examples present</b> - body example value directly</li>
 *   <li><b>Both declared, only param examples present</b> - {@code {"<parametersKey>": {...}}}</li>
 *   <li><b>Both declared, only body examples present</b> - {@code {"<requestBodyKey>": {...}}}</li>
 *   <li><b>Both declared, both have examples</b> - {@code {"<parametersKey>": {...}, "<requestBodyKey>": {...}}}</li>
 *   <li><b>Neither</b> - returns an empty list</li>
 * </ul>
 * <p>
 * The wrapping decision is driven by the presence of parameters and request body in the
 * operation definition, not by whether they carry examples. When both exist, the output is
 * always wrapped so that the MCP client can unambiguously distinguish parameters from the
 * request body.
 * <p>
 * Example extraction precedence per parameter in {@link ExamplesMode#ALL} mode:
 * {@code parameter.examples} &gt; {@code parameter.example} &gt; {@code parameter.schema.example}
 * <p>
 * In {@link ExamplesMode#ANNOTATED} mode only named {@code Example} objects carrying
 * {@code x-mcp-example: true} are eligible. Inline sources ({@code parameter.example},
 * {@code schema.example}, {@code mediaType.example}) are never included because they
 * cannot be annotated.
 * <p>
 * Example extraction precedence for request body (from {@code application/json} media type)
 * in {@link ExamplesMode#ALL} mode:
 * {@code mediaType.examples} &gt; {@code mediaType.example} &gt; {@code mediaType.schema.example}
 * <p>
 * Returns an empty list when configured with {@link ExamplesMode#SKIP}.
 */
@NullMarked
public class InputExampleComposer {

    private final String parametersKey;
    private final String requestBodyKey;
    private final ExamplesMode examplesMode;

    public InputExampleComposer(OpenApiMcpProperties properties) {
        this.parametersKey = properties.tools().schema().parametersKey();
        this.requestBodyKey = properties.tools().schema().requestBodyKey();
        this.examplesMode = properties.tools().examplesMode();
    }

    /**
     * Composes representative examples for the given operation using the configured
     * {@link ExamplesMode}.
     * <p>
     * Returns one {@link ComposedExample} per distinct body example found in the
     * {@code examples} map (each merged with the common parameter examples), or a
     * single entry for inline body examples / parameter-only operations.
     * Returns an empty list when the configured mode is {@link ExamplesMode#SKIP}.
     *
     * @param fullOperation the OpenAPI operation to extract examples from
     * @return an ordered list of composed examples; empty if no examples were found or mode is SKIP
     */
    public List<ComposedExample> composeExamples(FullOperation fullOperation) {
        if (examplesMode == ExamplesMode.SKIP) {
            return List.of();
        }
        var mode = examplesMode;
        var operation = fullOperation.operation();

        var parameters = operation.getParameters();
        var hasParameters = parameters != null && !parameters.isEmpty();
        var hasRequestBody = operation.getRequestBody() != null;

        var paramExamples = hasParameters ? extractParameterExamples(parameters, mode) : null;
        var bodyExamples =
                hasRequestBody ? extractBodyExamples(operation.getRequestBody(), mode) : List.<ComposedExample>of();

        if (!hasParameters || !hasRequestBody) {
            // Only one side present — return without wrapping
            if (bodyExamples.isEmpty()) {
                if (paramExamples == null) {
                    return List.of();
                }
                return List.of(new ComposedExample(null, null, paramExamples));
            }
            return bodyExamples;
        }

        // Both parameters and request body exist — always wrap under keys
        if (paramExamples == null && bodyExamples.isEmpty()) {
            return List.of();
        }

        if (bodyExamples.isEmpty()) {
            var wrapped = new LinkedHashMap<String, Object>();
            wrapped.put(parametersKey, paramExamples);
            return List.of(new ComposedExample(null, null, wrapped));
        }

        if (paramExamples == null) {
            var result = new ArrayList<ComposedExample>(bodyExamples.size());
            for (var bodyExample : bodyExamples) {
                var wrapped = new LinkedHashMap<String, Object>();
                wrapped.put(requestBodyKey, bodyExample.value());
                result.add(new ComposedExample(bodyExample.title(), bodyExample.description(), wrapped));
            }
            return Collections.unmodifiableList(result);
        }

        // Both have examples — merge param examples into every body example
        var result = new ArrayList<ComposedExample>(bodyExamples.size());
        for (var bodyExample : bodyExamples) {
            var combined = new LinkedHashMap<String, Object>();
            combined.put(parametersKey, paramExamples);
            combined.put(requestBodyKey, bodyExample.value());
            result.add(new ComposedExample(bodyExample.title(), bodyExample.description(), combined));
        }
        return Collections.unmodifiableList(result);
    }

    private @Nullable Map<String, Object> extractParameterExamples(List<Parameter> parameters, ExamplesMode mode) {
        var result = new LinkedHashMap<String, Object>();

        for (var parameter : parameters) {
            if (!SUPPORTED_PARAMETER_TYPES.contains(parameter.getIn())) {
                continue;
            }

            var example = extractParameterExample(parameter, mode);
            if (example != null) {
                result.put(parameter.getName(), example);
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Extracts an example value from a parameter.
     * <p>
     * In {@link ExamplesMode#ALL} mode the first entry of {@code parameter.examples} is used,
     * falling through to {@code parameter.example} and then {@code schema.example}.
     * <p>
     * In {@link ExamplesMode#ANNOTATED} mode only the first {@code parameter.examples} entry
     * annotated with {@code x-mcp-example: true} is returned. Inline sources
     * ({@code parameter.example}, {@code schema.example}) are never consulted because they
     * cannot carry the annotation.
     */
    private @Nullable Object extractParameterExample(Parameter parameter, ExamplesMode mode) {
        if (mode == ExamplesMode.ANNOTATED) {
            if (parameter.getExamples() != null && !parameter.getExamples().isEmpty()) {
                return extractFirstAnnotatedExampleValue(parameter.getExamples());
            }
            return null;
        }

        // ALL mode: parameter.examples > parameter.example > schema.example
        if (parameter.getExamples() != null && !parameter.getExamples().isEmpty()) {
            return extractFirstExampleValue(parameter.getExamples());
        }
        if (parameter.getExample() != null) {
            return parameter.getExample();
        }
        if (parameter.getSchema() != null && parameter.getSchema().getExample() != null) {
            return parameter.getSchema().getExample();
        }
        return null;
    }

    /**
     * Extracts all body examples from the {@code application/json} media type.
     * <p>
     * When the media type has a named {@code examples} map, each entry (with a non-null
     * value) becomes its own {@link ComposedExample} carrying the entry's {@code summary}
     * as the title, falling back to the map key when {@code summary} is absent or blank.
     * Inline {@code example} and {@code schema.example} sources each produce a single
     * unnamed entry.
     */
    private List<ComposedExample> extractBodyExamples(RequestBody requestBody, ExamplesMode mode) {
        if (requestBody.getContent() == null) {
            return List.of();
        }

        var mediaType = requestBody.getContent().get(DecomposedRequestData.SUPPORTED_MEDIA_TYPE.toString());
        if (mediaType == null) {
            return List.of();
        }

        return extractFromMediaType(mediaType, mode);
    }

    /**
     * Extracts examples from a media type.
     * <p>
     * In {@link ExamplesMode#ALL} mode the full {@code examples} map is returned, falling
     * through to {@code mediaType.example} and then {@code schema.example}.
     * <p>
     * In {@link ExamplesMode#ANNOTATED} mode only entries in the {@code examples} map
     * annotated with {@code x-mcp-example: true} are returned. Inline sources
     * ({@code mediaType.example}, {@code schema.example}) are never consulted because they
     * cannot carry the annotation.
     */
    private List<ComposedExample> extractFromMediaType(MediaType mediaType, ExamplesMode mode) {
        if (mode == ExamplesMode.ANNOTATED) {
            if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
                return extractAnnotatedExampleValues(mediaType.getExamples());
            }
            return List.of();
        }

        // ALL mode: mediaType.examples > mediaType.example > schema.example
        if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            return extractAllExampleValues(mediaType.getExamples());
        }
        if (mediaType.getExample() != null) {
            return List.of(new ComposedExample(null, null, mediaType.getExample()));
        }
        if (mediaType.getSchema() != null && mediaType.getSchema().getExample() != null) {
            return List.of(new ComposedExample(null, null, mediaType.getSchema().getExample()));
        }
        return List.of();
    }

    private @Nullable Object extractFirstExampleValue(Map<String, Example> examples) {
        return examples.values().stream()
                .filter(e -> e.getValue() != null)
                .map(Example::getValue)
                .findFirst()
                .orElse(null);
    }

    private @Nullable Object extractFirstAnnotatedExampleValue(Map<String, Example> examples) {
        return examples.values().stream()
                .filter(e -> e.getValue() != null && isMcpAnnotated(e))
                .map(Example::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<ComposedExample> extractAllExampleValues(Map<String, Example> examples) {
        var result = new ArrayList<ComposedExample>(examples.size());
        for (var entry : examples.entrySet()) {
            var example = entry.getValue();
            if (example.getValue() == null) {
                continue;
            }
            var summary = example.getSummary();
            var title = (summary != null && !summary.isBlank()) ? summary : entry.getKey();
            result.add(new ComposedExample(title, example.getDescription(), example.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    private List<ComposedExample> extractAnnotatedExampleValues(Map<String, Example> examples) {
        var result = new ArrayList<ComposedExample>();
        for (var entry : examples.entrySet()) {
            var example = entry.getValue();
            if (example.getValue() == null || !isMcpAnnotated(example)) {
                continue;
            }
            var summary = example.getSummary();
            var title = (summary != null && !summary.isBlank()) ? summary : entry.getKey();
            result.add(new ComposedExample(title, example.getDescription(), example.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    private boolean isMcpAnnotated(Example example) {
        var extensions = example.getExtensions();
        return extensions != null && Boolean.TRUE.equals(extensions.get(MCP_EXAMPLE_EXTENSION));
    }
}
