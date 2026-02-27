package com.infobip.openapi.mcp.openapi.schema;

import static com.infobip.openapi.mcp.openapi.schema.Spec.SUPPORTED_PARAMETER_TYPES;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
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
 * The composed example follows these combination rules (mirroring
 * {@code InputSchemaComposer.compose()}):
 * <ul>
 *   <li><b>Only parameters</b> - flat object {@code {"paramName": value, ...}}</li>
 *   <li><b>Only body</b> - body example value directly</li>
 *   <li><b>Both</b> - {@code {"<parametersKey>": {...}, "<requestBodyKey>": {...}}}</li>
 *   <li><b>Neither</b> - returns an empty list</li>
 * </ul>
 * <p>
 * Example extraction precedence per parameter:
 * {@code parameter.examples} &gt; {@code parameter.example} &gt; {@code parameter.schema.example}
 * <p>
 * Example extraction precedence for request body (from {@code application/json} media type):
 * {@code mediaType.examples} &gt; {@code mediaType.example} &gt; {@code mediaType.schema.example}
 */
@NullMarked
public class InputExampleComposer {

    private final String parametersKey;
    private final String requestBodyKey;

    public InputExampleComposer(OpenApiMcpProperties.Tools.Schema schemaProperties) {
        this.parametersKey = schemaProperties.parametersKey();
        this.requestBodyKey = schemaProperties.requestBodyKey();
    }

    /**
     * Composes representative examples for the given operation.
     * <p>
     * Returns one {@link ComposedExample} per distinct body example found in the
     * {@code examples} map (each merged with the common parameter examples), or a
     * single entry for inline body examples / parameter-only operations.
     *
     * @param fullOperation the OpenAPI operation to extract examples from
     * @return an ordered list of composed examples; empty if no examples were found
     */
    public List<ComposedExample> composeExamples(FullOperation fullOperation) {
        var operation = fullOperation.operation();

        var paramExamples =
                operation.getParameters() != null ? extractParameterExamples(operation.getParameters()) : null;

        var bodyExamples = operation.getRequestBody() != null
                ? extractBodyExamples(operation.getRequestBody())
                : List.<ComposedExample>of();

        if (bodyExamples.isEmpty()) {
            if (paramExamples == null) {
                return List.of();
            }
            return List.of(new ComposedExample(null, null, paramExamples));
        }

        if (paramExamples == null) {
            return bodyExamples;
        }

        // Merge param examples into every body example
        var result = new ArrayList<ComposedExample>(bodyExamples.size());
        for (var bodyExample : bodyExamples) {
            var combined = new LinkedHashMap<String, Object>();
            combined.put(parametersKey, paramExamples);
            combined.put(requestBodyKey, bodyExample.value());
            result.add(new ComposedExample(bodyExample.title(), bodyExample.description(), combined));
        }
        return Collections.unmodifiableList(result);
    }

    private @Nullable Map<String, Object> extractParameterExamples(List<Parameter> parameters) {
        var result = new LinkedHashMap<String, Object>();

        for (var parameter : parameters) {
            if (!SUPPORTED_PARAMETER_TYPES.contains(parameter.getIn())) {
                continue;
            }

            var example = extractParameterExample(parameter);
            if (example != null) {
                result.put(parameter.getName(), example);
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Extracts an example value from a parameter using the precedence:
     * parameter.examples > parameter.example > parameter.schema.example
     */
    private @Nullable Object extractParameterExample(Parameter parameter) {
        // 1. parameter.examples (Map<String, Example>)
        if (parameter.getExamples() != null && !parameter.getExamples().isEmpty()) {
            return extractFirstExampleValue(parameter.getExamples());
        }

        // 2. parameter.example (raw value)
        if (parameter.getExample() != null) {
            return parameter.getExample();
        }

        // 3. parameter.schema.example
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
    private List<ComposedExample> extractBodyExamples(RequestBody requestBody) {
        if (requestBody.getContent() == null) {
            return List.of();
        }

        var mediaType = requestBody.getContent().get(DecomposedRequestData.SUPPORTED_MEDIA_TYPE.toString());
        if (mediaType == null) {
            return List.of();
        }

        return extractFromMediaType(mediaType);
    }

    /**
     * Extracts examples from a media type using the precedence:
     * mediaType.examples > mediaType.example > mediaType.schema.example
     */
    private List<ComposedExample> extractFromMediaType(MediaType mediaType) {
        // 1. mediaType.examples (Map<String, Example>) — one ComposedExample per entry
        if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            return extractAllExampleValues(mediaType.getExamples());
        }

        // 2. mediaType.example (raw value)
        if (mediaType.getExample() != null) {
            return List.of(new ComposedExample(null, null, mediaType.getExample()));
        }

        // 3. mediaType.schema.example
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
}
