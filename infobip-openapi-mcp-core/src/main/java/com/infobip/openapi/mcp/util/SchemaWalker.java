package com.infobip.openapi.mcp.util;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;

/**
 * SchemaWalker is a utility class that can be used to access all
 * Schemas defined in an OpenAPI specification. Instantiate the
 * SchemaWalker by providing it with a schema consumer, and
 * pass the specification as an argument to its walk method.
 * SchemaWalker will invoke the visitor with every schema present
 * in the provided specification.
 */
public class SchemaWalker {

    private final @NonNull Consumer<@NonNull Schema<?>> schemaVisitor;

    /**
     * @param schemaVisitor that will be invoked for every schema in the specification.
     */
    public SchemaWalker(@NonNull Consumer<@NonNull Schema<?>> schemaVisitor) {
        this.schemaVisitor = schemaVisitor;
    }

    /**
     * @param openApi specification that will be traversed
     */
    public void walk(@NonNull OpenAPI openApi) {
        Stream.concat(schemasFromPaths(openApi), schemasFromComponents(openApi)).forEach(this::visitAllSchemasIn);
    }

    private Stream<Schema> schemasFromComponents(OpenAPI openApi) {
        return Optional.ofNullable(openApi.getComponents()).map(Components::getSchemas).stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull);
    }

    private Stream<Schema> schemasFromPaths(OpenAPI openApi) {
        return Optional.ofNullable(openApi.getPaths()).stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(PathItem::readOperations)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .flatMap(this::schemasFromOperation)
                .filter(Objects::nonNull);
    }

    private Stream<Schema> schemasFromOperation(Operation operation) {
        var parameters = schemasFromParameters(operation);
        var requests = schemasFromRequest(operation);
        var responses = schemasFromResponses(operation);
        return Stream.concat(parameters, Stream.concat(requests, responses));
    }

    private Stream<Schema> schemasFromParameters(Operation operation) {
        return Optional.ofNullable(operation.getParameters()).stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(Parameter::getSchema)
                .filter(Objects::nonNull);
    }

    private Stream<Schema> schemasFromRequest(Operation operation) {
        return Optional.ofNullable(operation.getRequestBody()).map(RequestBody::getContent).stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(MediaType::getSchema)
                .filter(Objects::nonNull);
    }

    private Stream<Schema> schemasFromResponses(Operation operation) {
        return Optional.ofNullable(operation.getResponses()).map(Map::values).stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(ApiResponse::getContent)
                .filter(Objects::nonNull)
                .map(Content::values)
                .flatMap(Collection::stream)
                .map(MediaType::getSchema)
                .filter(Objects::nonNull);
    }

    private void visitAllSchemasIn(Schema<?> schema) {
        if (schema == null) {
            return;
        }

        schemaVisitor.accept(schema);

        visitAllSchemasIn(schema.getIf());
        visitAllSchemasIn(schema.getNot());
        visitAllSchemasIn(schema.getElse());
        visitAllSchemasIn(schema.getThen());
        visitAllSchemasIn(schema.getContains());
        visitAllSchemasIn(schema.getContentSchema());
        visitAllSchemasIn(schema.getItems());
        visitAllSchemasIn(schema.getUnevaluatedItems());
        visitAllSchemasIn(schema.getAdditionalItems());

        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(this::visitAllSchemasIn);
        }
        if (schema.getDependentSchemas() != null) {
            schema.getDependentSchemas().values().forEach(this::visitAllSchemasIn);
        }
        if (schema.getPrefixItems() != null) {
            schema.getPrefixItems().forEach(this::visitAllSchemasIn);
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(this::visitAllSchemasIn);
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(this::visitAllSchemasIn);
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(this::visitAllSchemasIn);
        }
    }
}
