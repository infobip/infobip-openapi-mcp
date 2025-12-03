package com.infobip.openapi.mcp.openapi.filter;

import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * DiscriminatorFlattener is an OpenAPIFilter that flattens the discriminator properties in the OpenAPI specification.
 * This is useful for simplifying the` schema definitions by removing discriminators.
 * <p>
 * This class will perform the following operations:
 * 1. Traverse the OpenAPI specification.
 * 2. Identify discriminator properties in schemas.
 * 3. Replace those schemas with an oneOf with all the possible schemas defined by the discriminator.
 * 4. Remove the discriminator property from the schema.
 * <p>
 * MCP tool definition requires a valid input JSON Schema. Discriminators are a part of the OpenAPI specification
 * dialect, not a part of the JSON Schema definition. Discriminators are especially useful when processing the actual
 * request (on the server side), but they are not so useful when generating the input. The resulting oneOf schema
 * should be sufficient for the MCP tool definition.
 */
@Order
@NullMarked
public class DiscriminatorFlattener implements OpenApiFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscriminatorFlattener.class);

    @Override
    public OpenAPI filter(OpenAPI openApi) {
        var components = Optional.ofNullable(openApi.getComponents())
                .map(Components::getSchemas)
                .orElse(Map.of());

        var visitedSchemas = new HashSet<String>();

        for (var schemaEntry : components.entrySet()) {
            var schemaName = schemaEntry.getKey();
            var schema = schemaEntry.getValue();
            visitedSchemas.add(schemaName);
            processDiscriminator(schema, components, visitedSchemas);
        }

        return openApi;
    }

    private void processDiscriminator(
            @Nullable Schema<?> schemaToProcess,
            Map<String, Schema> components,
            Set<String> schemasVisitedDuringTraversal) {
        if (schemaToProcess == null) {
            return;
        }
        if (schemaToProcess.get$ref() != null) {
            // If the schema is a reference, it will be processed as a part of the components schema traversal.
            // The schema resolution is handled in the later stage, so we can skip it here.
            return;
        }

        var discriminator = schemaToProcess.getDiscriminator();
        if (discriminator != null) {
            // If the schema has a discriminator, we need to process it.
            // We will replace the schema with an oneOf schema that contains all
            // the possible schemas defined by the discriminator.
            var propertyName = discriminator.getPropertyName();
            var mapping = discriminator.getMapping();
            if (mapping != null) {
                for (var mappingEntry : mapping.entrySet()) {
                    var propertyValue = mappingEntry.getKey();
                    var schemaName =
                            RefUtils.extractSimpleName(mappingEntry.getValue()).getKey();
                    var referencedSchema = components.get(schemaName);
                    if (referencedSchema == null) {
                        LOGGER.warn(
                                "Referenced schema '{}' not found in components for discriminator '{}'. "
                                        + "It will be skipped.",
                                mappingEntry.getValue(),
                                propertyName);
                        continue;
                    }

                    // If the referenced schema is a simple schema, look for the property
                    if (referencedSchema.getProperties() != null
                            && referencedSchema.getProperties().containsKey(propertyName)) {
                        var adjustedSchema =
                                adjustSchemaWithDiscriminatorProperty(referencedSchema, propertyName, propertyValue);
                        schemaToProcess.addOneOfItem(adjustedSchema);
                    } else if (referencedSchema.getAllOf() != null) {
                        // If the referenced schema is an allOf, we need to add the discriminator property
                        // to the correct allOf schema. We support only one nesting level of allOf.
                        var isPropertyFound = false;
                        var allOfSchemas = (List<Schema>) referencedSchema.getAllOf();
                        var resolvedAllOfSchemas = new ArrayList<Schema>();
                        for (Schema<?> allOfSchema : allOfSchemas) {
                            if (isPropertyFound) {
                                // If we already found the property, we can skip processing the rest of the allOf
                                // schemas.
                                resolvedAllOfSchemas.add(allOfSchema);
                                continue;
                            }
                            if (allOfSchema.get$ref() != null) {
                                var allOfSchemaName = RefUtils.extractSimpleName(allOfSchema.get$ref())
                                        .getKey();
                                if (components.containsKey(allOfSchemaName)) {
                                    var referencedAllOfSchema = components.get(allOfSchemaName);
                                    if (referencedAllOfSchema.getProperties() != null
                                            && referencedAllOfSchema
                                                    .getProperties()
                                                    .containsKey(propertyName)) {
                                        isPropertyFound = true;
                                        var adjustedSchema = adjustSchemaWithDiscriminatorProperty(
                                                referencedAllOfSchema, propertyName, propertyValue);
                                        if (schemasVisitedDuringTraversal.contains(allOfSchemaName)) {
                                            // Explicitly remove the discriminator from the allOf schema
                                            // to avoid infinite recursion
                                            adjustedSchema.setDiscriminator(null);
                                        }
                                        resolvedAllOfSchemas.add(adjustedSchema);
                                    }
                                }
                            } else if (allOfSchema.getProperties() != null
                                    && allOfSchema.getProperties().containsKey(propertyName)) {
                                isPropertyFound = true;
                                var adjustedSchema =
                                        adjustSchemaWithDiscriminatorProperty(allOfSchema, propertyName, propertyValue);
                                resolvedAllOfSchemas.add(adjustedSchema);
                            } else {
                                resolvedAllOfSchemas.add(allOfSchema);
                            }
                        }
                        referencedSchema.setAllOf(resolvedAllOfSchemas);
                        schemaToProcess.addOneOfItem(referencedSchema);
                    } else {
                        LOGGER.warn(
                                "Discriminator property '{}' not found in the referenced schema '{}'. "
                                        + "It will be appended as is.",
                                propertyName,
                                mappingEntry.getValue());
                        schemaToProcess.addOneOfItem(referencedSchema);
                    }
                }
                schemaToProcess.setProperties(null); // Remove the properties to avoid confusion with oneOf
            } else {
                LOGGER.warn("Discriminator '{}' does not have mapping defined. It will be skipped.", propertyName);
            }
            schemaToProcess.setDiscriminator(null); // Remove the discriminator property
            return;
        }

        // If the schema is a composed schema, we need to process its sub-schemas.
        // We need to look for the discriminator of all non-ref schemas.
        if (schemaToProcess.getAllOf() != null) {
            for (var allOfSchema : schemaToProcess.getAllOf()) {
                processDiscriminator(allOfSchema, components, schemasVisitedDuringTraversal);
            }
        }
        if (schemaToProcess.getAnyOf() != null) {
            for (var anyOfSchema : schemaToProcess.getAnyOf()) {
                processDiscriminator(anyOfSchema, components, schemasVisitedDuringTraversal);
            }
        }
        if (schemaToProcess.getOneOf() != null) {
            for (var oneOfSchema : schemaToProcess.getOneOf()) {
                processDiscriminator(oneOfSchema, components, schemasVisitedDuringTraversal);
            }
        }

        // Process array-related schema properties (OpenAPI 3.1)
        if (schemaToProcess.getPrefixItems() != null) {
            for (var prefixItemSchema : schemaToProcess.getPrefixItems()) {
                processDiscriminator(prefixItemSchema, components, schemasVisitedDuringTraversal);
            }
        }
        if (schemaToProcess.getItems() != null) {
            processDiscriminator(schemaToProcess.getItems(), components, schemasVisitedDuringTraversal);
        }
        if (schemaToProcess.getUnevaluatedItems() != null) {
            processDiscriminator(schemaToProcess.getUnevaluatedItems(), components, schemasVisitedDuringTraversal);
        }
        if (schemaToProcess.getContains() != null) {
            processDiscriminator(schemaToProcess.getContains(), components, schemasVisitedDuringTraversal);
        }

        // Process object-related schema properties (OpenAPI 3.1)
        if (schemaToProcess.getDependentSchemas() != null) {
            for (var dependentSchema : schemaToProcess.getDependentSchemas().values()) {
                processDiscriminator(dependentSchema, components, schemasVisitedDuringTraversal);
            }
        }

        // Process conditional schema properties (OpenAPI 3.1)
        if (schemaToProcess.getElse() != null) {
            processDiscriminator(schemaToProcess.getElse(), components, schemasVisitedDuringTraversal);
        }
        if (schemaToProcess.getNot() != null) {
            processDiscriminator(schemaToProcess.getNot(), components, schemasVisitedDuringTraversal);
        }

        // Process content schema (OpenAPI 3.1)
        if (schemaToProcess.getContentSchema() != null) {
            processDiscriminator(schemaToProcess.getContentSchema(), components, schemasVisitedDuringTraversal);
        }

        // Finally, look for the discriminator in all non-ref property schemas.
        if (schemaToProcess.getProperties() != null) {
            for (var propertySchema : schemaToProcess.getProperties().values()) {
                processDiscriminator(propertySchema, components, schemasVisitedDuringTraversal);
            }
        }
    }

    private Schema<?> adjustSchemaWithDiscriminatorProperty(
            Schema<?> originalSchema, String discriminatorPropertyName, String discriminatorPropertyValueToSet) {
        var adjustedSchema = new Schema<>();
        originalSchema.getProperties().forEach((name, propertySchema) -> {
            if (name.equals(discriminatorPropertyName)) {
                // Replace the discriminator property with a string schema
                var stringSchema = new StringSchema();
                stringSchema
                        ._enum(List.of(discriminatorPropertyValueToSet))
                        ._default(discriminatorPropertyValueToSet)
                        .description("Always set to '" + discriminatorPropertyValueToSet + "'.");
                adjustedSchema.addProperty(name, stringSchema);
            } else {
                // Keep other properties as they are
                adjustedSchema.addProperty(name, propertySchema);
            }
        });

        if (originalSchema.getTypes() != null) {
            adjustedSchema.setTypes(originalSchema.getTypes());
        } else if (originalSchema.getType() != null) {
            adjustedSchema.setType(originalSchema.getType());
        }

        return adjustedSchema
                .required(originalSchema.getRequired())
                .description(originalSchema.getDescription())
                .additionalProperties(originalSchema.getAdditionalProperties())
                .discriminator(originalSchema.getDiscriminator());
    }
}
