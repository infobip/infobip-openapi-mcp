package com.infobip.openapi.mcp.openapi.filter;

import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        for (var schemaEntry : components.entrySet()) {
            processDiscriminator(schemaEntry.getValue(), components);
        }

        return openApi;
    }

    private void processDiscriminator(@Nullable Schema<?> schemaToProcess, Map<String, Schema> components) {
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
                        var adjustedSchema = adjustSchemaWithDiscriminatorProperty(
                                referencedSchema, propertyName, propertyValue, schemaToProcess.getDescription());
                        schemaToProcess.addOneOfItem(adjustedSchema);
                    } else if (referencedSchema.getAllOf() != null) {
                        // If the referenced schema is an allOf, we need to add the discriminator property
                        // to the correct allOf schema. We support only one nesting level of allOf.
                        var isPropertyFound = false;
                        var allOfSchemas = (List<Schema>) referencedSchema.getAllOf();
                        var resolvedAllOfSchemas = new ArrayList<Schema>();
                        for (Schema<?> allOfSchema : allOfSchemas) {
                            if (allOfSchema.get$ref() != null) {
                                var allOfSchemaName = RefUtils.extractSimpleName(allOfSchema.get$ref())
                                        .getKey();
                                if (components.containsKey(allOfSchemaName)) {
                                    var referencedAllOfSchema = components.get(allOfSchemaName);
                                    if (referencedAllOfSchema.getProperties() != null
                                            && referencedAllOfSchema
                                                    .getProperties()
                                                    .containsKey(propertyName)) {
                                        if (isPropertyFound) {
                                            int propertyCount = referencedAllOfSchema
                                                    .getProperties()
                                                    .size();
                                            LOGGER.warn(
                                                    "Multiple schemas define the same discriminator property '{}'. "
                                                            + "AllOf component '{}' will be skipped as the property has already been adjusted during schema {} processing. "
                                                            + "Skipped schema had {} properties.",
                                                    propertyName,
                                                    allOfSchemaName,
                                                    schemaName,
                                                    propertyCount);
                                            continue;
                                        }
                                        isPropertyFound = true;
                                        var adjustedSchema = adjustSchemaWithDiscriminatorProperty(
                                                referencedAllOfSchema,
                                                propertyName,
                                                propertyValue,
                                                schemaToProcess.getDescription());
                                        resolvedAllOfSchemas.add(adjustedSchema);
                                    }
                                }
                            } else if (allOfSchema.getProperties() != null
                                    && allOfSchema.getProperties().containsKey(propertyName)) {
                                if (isPropertyFound) {
                                    int propertyCount =
                                            allOfSchema.getProperties().size();
                                    LOGGER.warn(
                                            "Multiple schemas define the same discriminator property '{}'. "
                                                    + "Inline allOf schema will be skipped as the property has already been adjusted during schema {} processing. "
                                                    + "Skipped schema had {} properties.",
                                            propertyName,
                                            schemaName,
                                            propertyCount);
                                    continue;
                                }
                                isPropertyFound = true;
                                var adjustedSchema = adjustSchemaWithDiscriminatorProperty(
                                        allOfSchema, propertyName, propertyValue, schemaToProcess.getDescription());
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
                processDiscriminator(allOfSchema, components);
            }
        }
        if (schemaToProcess.getAnyOf() != null) {
            for (var anyOfSchema : schemaToProcess.getAnyOf()) {
                processDiscriminator(anyOfSchema, components);
            }
        }
        if (schemaToProcess.getOneOf() != null) {
            for (var oneOfSchema : schemaToProcess.getOneOf()) {
                processDiscriminator(oneOfSchema, components);
            }
        }

        // Process array-related schema properties (OpenAPI 3.1)
        if (schemaToProcess.getPrefixItems() != null) {
            for (var prefixItemSchema : schemaToProcess.getPrefixItems()) {
                processDiscriminator(prefixItemSchema, components);
            }
        }
        if (schemaToProcess.getItems() != null) {
            processDiscriminator(schemaToProcess.getItems(), components);
        }
        if (schemaToProcess.getUnevaluatedItems() != null) {
            processDiscriminator(schemaToProcess.getUnevaluatedItems(), components);
        }
        if (schemaToProcess.getContains() != null) {
            processDiscriminator(schemaToProcess.getContains(), components);
        }

        // Process object-related schema properties (OpenAPI 3.1)
        if (schemaToProcess.getDependentSchemas() != null) {
            for (var dependentSchema : schemaToProcess.getDependentSchemas().values()) {
                processDiscriminator(dependentSchema, components);
            }
        }

        // Process conditional schema properties (OpenAPI 3.1)
        if (schemaToProcess.getElse() != null) {
            processDiscriminator(schemaToProcess.getElse(), components);
        }
        if (schemaToProcess.getNot() != null) {
            processDiscriminator(schemaToProcess.getNot(), components);
        }

        // Process content schema (OpenAPI 3.1)
        if (schemaToProcess.getContentSchema() != null) {
            processDiscriminator(schemaToProcess.getContentSchema(), components);
        }

        // Finally, look for the discriminator in all non-ref property schemas.
        if (schemaToProcess.getProperties() != null) {
            for (var propertySchema : schemaToProcess.getProperties().values()) {
                processDiscriminator(propertySchema, components);
            }
        }
    }

    private Schema<?> adjustSchemaWithDiscriminatorProperty(
            Schema<?> originalSchema,
            String discriminatorPropertyName,
            String discriminatorPropertyValueToSet,
            @Nullable String parentSchemaDescription) {
        var adjustedSchema = new Schema<>();
        originalSchema.getProperties().forEach((name, propertySchema) -> {
            if (name.equals(discriminatorPropertyName)) {
                // Replace the discriminator property with a string schema
                var stringSchema = new StringSchema();
                stringSchema
                        ._enum(List.of(discriminatorPropertyValueToSet))
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

        // If the original schema description is the same as the parent schema description,
        // use the discriminator property value as the description instead
        String descriptionToUse = originalSchema.getDescription();
        if (parentSchemaDescription != null && parentSchemaDescription.equals(originalSchema.getDescription())) {
            descriptionToUse = discriminatorPropertyValueToSet;
        }

        return adjustedSchema
                .required(originalSchema.getRequired())
                .description(descriptionToUse)
                .additionalProperties(originalSchema.getAdditionalProperties());
    }
}
