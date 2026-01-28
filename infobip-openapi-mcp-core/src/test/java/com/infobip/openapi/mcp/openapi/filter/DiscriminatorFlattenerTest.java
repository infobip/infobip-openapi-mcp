package com.infobip.openapi.mcp.openapi.filter;

import static org.assertj.core.api.BDDAssertions.then;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class DiscriminatorFlattenerTest {

    private DiscriminatorFlattener flattener;
    private OpenAPIV3Parser parser;

    @BeforeEach
    void setUp() {
        flattener = new DiscriminatorFlattener();
        parser = new OpenAPIV3Parser();
    }

    private String flatten(String inputSpec) throws Exception {
        var parsed = parser.readContents(inputSpec, null, null);
        then(parsed).as("Spec should parse").isNotNull();
        OpenAPI openApi = parsed.getOpenAPI();
        then(openApi).as("Parsed OpenAPI should not be null").isNotNull();
        var result = flattener.filter(openApi); // in-place transform
        return Json31.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private void assertFlattened(String input, String expectedJson) throws Exception {
        var actual = flatten(input);
        JSONAssert.assertEquals(expectedJson, actual, JSONCompareMode.STRICT);
    }

    @Nested
    class NoDiscriminator {
        @Test
        void unchangedWhenNoDiscriminatorsPresent() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": {
                    "title": "API",
                    "version": "1"
                  },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "User": {
                        "type": "object",
                        "properties": {
                          "id": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, input);
        }

        @Test
        void emptyComponentsRemainsSame() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": { "schemas": { } }
                }
                """;
            assertFlattened(input, input);
        }

        @Test
        void noComponentsRemainsSame() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "info": { "title": "API", "version": "1" }
                }
                """;
            assertFlattened(input, input);
        }
    }

    @Nested
    class SimpleMapping {
        @Test
        @DisplayName("Discriminator replaced with oneOf; mapped schema properties converted to enum/default.")
        void simpleDiscriminatorMapping() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "kind",
                          "mapping": {
                            "a": "#/components/schemas/A",
                            "b": "#/components/schemas/B"
                          }
                        },
                        "properties": {
                          "kind": { "type": "string" },
                          "common": { "type": "string" }
                        }
                      },
                      "A": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string" },
                          "aProp": { "type": "integer" }
                        }
                      },
                      "B": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string" },
                          "bProp": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "properties": {
                              "kind": {
                                "type": "string",
                                "enum": [ "a" ],
                                "default": "a",
                                "description": "Always set to 'a'."
                              },
                              "aProp": { "type": "integer" }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "kind": {
                                "type": "string",
                                "enum": [ "b" ],
                                "default": "b",
                                "description": "Always set to 'b'."
                              },
                              "bProp": { "type": "boolean" }
                            }
                          }
                        ]
                      },
                      "A": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string" },
                          "aProp": { "type": "integer" }
                        }
                      },
                      "B": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string" },
                          "bProp": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Missing referenced schema in mapping is skipped")
        void missingReferencedSchemaSkipped() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Parent": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "type",
                          "mapping": {
                            "ok": "#/components/schemas/Ok",
                            "missing": "#/components/schemas/Missing"
                          }
                        },
                        "properties": { "type": { "type": "string" } }
                      },
                      "Ok": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "value": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Parent": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [ "ok" ],
                                "default": "ok",
                                "description": "Always set to 'ok'."
                              },
                              "value": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "Ok": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "value": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator without mapping is removed; no oneOf created")
        void discriminatorWithoutMapping() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Entity": {
                        "type": "object",
                        "discriminator": { "propertyName": "dtype" },
                        "properties": {
                          "dtype": { "type": "string" },
                          "id": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Entity": {
                        "type": "object",
                        "properties": {
                          "dtype": { "type": "string" },
                          "id": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    class AllOfComposition {
        @Test
        @DisplayName("Discriminator mapping where referenced schema has allOf; property located in inline allOf schema")
        void allOfInlineProperty() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "discriminator": { "propertyName": "kind", "mapping": { "x": "#/components/schemas/X" } },
                        "properties": { "kind": { "type": "string" } }
                      },
                      "X": {
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "kind": { "type": "string" },
                              "xval": { "type": "integer" }
                            }
                          },
                          {
                            "type": "object",
                            "properties": { "extra": { "type": "string" } }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            // Due to the way how OpenAPI parser reuses references,
            // the "X" component schema also gets modified in the result.
            // This is acceptable and does not affect the correctness of the flattening.
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "oneOf": [
                          {
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "kind": {
                                    "type": "string",
                                    "enum": [ "x" ],
                                    "default": "x",
                                    "description": "Always set to 'x'."
                                  },
                                  "xval": { "type": "integer" }
                                }
                              },
                              {
                                "type": "object",
                                "properties": { "extra": { "type": "string" } }
                              }
                            ]
                          }
                        ]
                      },
                      "X": {
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "kind": {
                                "type": "string",
                                "enum": [ "x" ],
                                "default": "x",
                                "description": "Always set to 'x'."
                              },
                              "xval": { "type": "integer" }
                            }
                          },
                          {
                            "type": "object",
                            "properties": { "extra": { "type": "string" } }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName(
                "Discriminator mapping where property is only in referenced schema via $ref inside allOf no inline match (no adjustment)")
        void allOfWithRefOnlyPropertyInRef() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "properties": { "kind": { "type": "string" } }
                      },
                      "Vehicle": {
                        "type": "object",
                        "discriminator": { "propertyName": "kind", "mapping": { "car": "#/components/schemas/Car" } },
                        "properties": { "kind": { "type": "string" } }
                      },
                      "Car": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/Base" },
                          {
                            "type": "object",
                            "properties": { "model": { "type": "string" } }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "properties": { "kind": { "type": "string" } }
                      },
                      "Vehicle": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "kind": {
                                    "type": "string",
                                    "enum": [ "car" ],
                                    "default": "car",
                                    "description": "Always set to 'car'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": { "model": { "type": "string" } }
                              }
                            ]
                          }
                        ]
                      },
                      "Car": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "kind": {
                                "type": "string",
                                "enum": [ "car" ],
                                "default": "car",
                                "description": "Always set to 'car'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": { "model": { "type": "string" } }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName(
                "Discriminator mapping where property is only in referenced schema via $ref inside allOf (ref second in array)")
        void allOfWithRefOnlyPropertyInRefInvertedOrder() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "properties": { "kind": { "type": "string" } }
                      },
                      "Vehicle": {
                        "type": "object",
                        "discriminator": { "propertyName": "kind", "mapping": { "car": "#/components/schemas/Car" } },
                        "properties": { "kind": { "type": "string" } }
                      },
                      "Car": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": { "model": { "type": "string" } }
                          },
                          { "$ref": "#/components/schemas/Base" }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "properties": { "kind": { "type": "string" } }
                      },
                      "Vehicle": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": { "model": { "type": "string" } }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "kind": {
                                    "type": "string",
                                    "enum": [ "car" ],
                                    "default": "car",
                                    "description": "Always set to 'car'."
                                  }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "Car": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": { "model": { "type": "string" } }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "kind": {
                                "type": "string",
                                "enum": [ "car" ],
                                "default": "car",
                                "description": "Always set to 'car'."
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    class NestedAndPropertyLevel {
        @Test
        @DisplayName("Discriminator inside property schema is flattened")
        void nestedPropertyDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Container": {
                        "type": "object",
                        "properties": {
                          "item": {
                            "type": "object",
                            "discriminator": { "propertyName": "t", "mapping": { "book": "#/components/schemas/Book" } },
                            "properties": {
                              "t": { "type": "string" },
                              "p": { "type": "string" }
                            }
                          }
                        }
                      },
                      "Book": {
                        "type": "object",
                        "properties": {
                          "t": { "type": "string" },
                          "title": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Container": {
                        "type": "object",
                        "properties": {
                          "item": {
                            "type": "object",
                            "oneOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "t": {
                                    "type": "string",
                                    "enum": [ "book" ],
                                    "default": "book",
                                    "description": "Always set to 'book'."
                                  },
                                  "title": { "type": "string" }
                                }
                              }
                            ]
                          }
                        }
                      },
                      "Book": {
                        "type": "object",
                        "properties": {
                          "t": { "type": "string" },
                          "title": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    class RecursionAndRefs {
        @Test
        @DisplayName("Circular reference: discriminator schema is referenced back in mapped schema's allOf")
        void circularReferenceInAllOf() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseAction": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "actionType",
                          "mapping": {
                            "specific": "#/components/schemas/SpecificAction"
                          }
                        },
                        "properties": {
                          "actionType": { "type": "string" }
                        }
                      },
                      "SpecificAction": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseAction" },
                          {
                            "type": "object",
                            "properties": {
                              "extraField": { "type": "string" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseAction": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "actionType": {
                                    "type": "string",
                                    "enum": [ "specific" ],
                                    "default": "specific",
                                    "description": "Always set to 'specific'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "extraField": { "type": "string" }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "SpecificAction": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "actionType": {
                                "type": "string",
                                "enum": [ "specific" ],
                                "default": "specific",
                                "description": "Always set to 'specific'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "extraField": { "type": "string" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator property defined as $ref with simple allOf mappings")
        void discriminatorPropertyAsRefWithSimpleAllOf() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "TypeEnum": {
                        "type": "string",
                        "enum": ["TEXT", "INTEGER"]
                      },
                      "BaseRequirement": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "type",
                          "mapping": {
                            "TEXT": "#/components/schemas/TextRequirement",
                            "INTEGER": "#/components/schemas/IntegerRequirement"
                          }
                        },
                        "properties": {
                          "type": {
                            "$ref": "#/components/schemas/TypeEnum"
                          }
                        }
                      },
                      "TextRequirement": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseRequirement" },
                          {
                            "type": "object",
                            "properties": {
                              "value": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "IntegerRequirement": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseRequirement" },
                          {
                            "type": "object",
                            "properties": {
                              "value": { "type": "integer" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "TypeEnum": {
                        "type": "string",
                        "enum": ["TEXT", "INTEGER"]
                      },
                      "BaseRequirement": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [ "TEXT" ],
                                    "default": "TEXT",
                                    "description": "Always set to 'TEXT'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "value": { "type": "string" }
                                }
                              }
                            ]
                          },
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [ "INTEGER" ],
                                    "default": "INTEGER",
                                    "description": "Always set to 'INTEGER'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "value": { "type": "integer" }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "TextRequirement": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [ "TEXT" ],
                                "default": "TEXT",
                                "description": "Always set to 'TEXT'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "value": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "IntegerRequirement": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [ "INTEGER" ],
                                "default": "INTEGER",
                                "description": "Always set to 'INTEGER'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "value": { "type": "integer" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Nested discriminators with $ref properties - configuration pattern")
        void nestedDiscriminatorsWithRefProperties() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "TypeA": {
                        "type": "string",
                        "enum": ["OPTION_A", "OPTION_B"]
                      },
                      "TypeB": {
                        "type": "string",
                        "enum": ["MODE_X", "MODE_Y"]
                      },
                      "BaseConfig": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "category",
                          "mapping": {
                            "OPTION_A": "#/components/schemas/SpecificConfig"
                          }
                        },
                        "properties": {
                          "category": {
                            "$ref": "#/components/schemas/TypeA"
                          }
                        }
                      },
                      "BaseAction": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "mode",
                          "mapping": {
                            "MODE_X": "#/components/schemas/SpecificAction"
                          }
                        },
                        "properties": {
                          "mode": {
                            "$ref": "#/components/schemas/TypeB"
                          }
                        }
                      },
                      "SpecificConfig": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseConfig" },
                          {
                            "type": "object",
                            "properties": {
                              "identifier": { "type": "string" },
                              "action": {
                                "$ref": "#/components/schemas/BaseAction"
                              }
                            }
                          }
                        ]
                      },
                      "SpecificAction": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseAction" },
                          {
                            "type": "object",
                            "properties": {
                              "endpoint": { "type": "string" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "TypeA": {
                        "type": "string",
                        "enum": ["OPTION_A", "OPTION_B"]
                      },
                      "TypeB": {
                        "type": "string",
                        "enum": ["MODE_X", "MODE_Y"]
                      },
                      "BaseConfig": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "category": {
                                    "type": "string",
                                    "enum": [ "OPTION_A" ],
                                    "default": "OPTION_A",
                                    "description": "Always set to 'OPTION_A'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "identifier": { "type": "string" },
                                  "action": {
                                    "$ref": "#/components/schemas/BaseAction"
                                  }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "BaseAction": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "mode": {
                                    "type": "string",
                                    "enum": [ "MODE_X" ],
                                    "default": "MODE_X",
                                    "description": "Always set to 'MODE_X'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "endpoint": { "type": "string" }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "SpecificConfig": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "category": {
                                "type": "string",
                                "enum": [ "OPTION_A" ],
                                "default": "OPTION_A",
                                "description": "Always set to 'OPTION_A'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "identifier": { "type": "string" },
                              "action": {
                                "$ref": "#/components/schemas/BaseAction"
                              }
                            }
                          }
                        ]
                      },
                      "SpecificAction": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "mode": {
                                "type": "string",
                                "enum": [ "MODE_X" ],
                                "default": "MODE_X",
                                "description": "Always set to 'MODE_X'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "endpoint": { "type": "string" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator in nested property creates infinite loop when mapped schema has properties")
        void discriminatorInPropertyWithCircularProperties() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Action": {
                        "type": "object",
                        "properties": {
                          "config": {
                            "type": "object",
                            "discriminator": {
                              "propertyName": "type",
                              "mapping": {
                                "forward": "#/components/schemas/ForwardAction"
                              }
                            },
                            "properties": {
                              "type": { "type": "string" }
                            }
                          }
                        }
                      },
                      "ForwardAction": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "nestedAction": {
                            "$ref": "#/components/schemas/Action"
                          }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Action": {
                        "type": "object",
                        "properties": {
                          "config": {
                            "type": "object",
                            "oneOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [ "forward" ],
                                    "default": "forward",
                                    "description": "Always set to 'forward'."
                                  },
                                  "nestedAction": {
                                    "$ref": "#/components/schemas/Action"
                                  }
                                }
                              }
                            ]
                          }
                        }
                      },
                      "ForwardAction": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "nestedAction": {
                            "$ref": "#/components/schemas/Action"
                          }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName(
                "Visited schema logic prevents infinite recursion when allOf references earlier discriminator root")
        void preventInfiniteRecursion() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Node": {
                        "type": "object",
                        "discriminator": { "propertyName": "dtype", "mapping": { "leaf": "#/components/schemas/Leaf" } },
                        "properties": { "dtype": { "type": "string" } }
                      },
                      "Leaf": {
                        "allOf": [
                          { "$ref": "#/components/schemas/Node" },
                          { "type": "object", "properties": { "value": { "type": "string" } } }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Node": {
                        "type": "object",
                        "oneOf": [
                          {
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "dtype": {
                                    "type": "string",
                                    "enum": [ "leaf" ],
                                    "default": "leaf",
                                    "description": "Always set to 'leaf'."
                                  }
                                }
                              },
                              { "type": "object", "properties": { "value": { "type": "string" } } }
                            ]
                          }
                        ]
                      },
                      "Leaf": {
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "dtype": {
                                "type": "string",
                                "enum": [ "leaf" ],
                                "default": "leaf",
                                "description": "Always set to 'leaf'."
                              }
                            }
                          },
                          { "type": "object", "properties": { "value": { "type": "string" } } }
                        ]
                      }
                    }
                  }
                }
               """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("$ref schemas (standalone) are not modified directly")
        void refSchemasUnchanged() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Base": {
                        "type": "object",
                        "properties": { "id": { "type": "string" } }
                      },
                      "Wrapper": {
                        "type": "object",
                        "properties": { "b": { "$ref": "#/components/schemas/Base" } }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, input);
        }
    }

    @Nested
    class MultipleMappingsAndUnrelated {
        @Test
        @DisplayName(
                "Multiple mappings with allOf where property is in referenced schema - all allOf items preserved with correct type info")
        void multipleMappingsWithAllOfReferencedProperty() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseType": {
                        "type": "object",
                        "properties": { "type": { "type": "string" } }
                      },
                      "Vehicle": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "type",
                          "mapping": {
                            "car": "#/components/schemas/Car",
                            "bike": "#/components/schemas/Bike"
                          }
                        },
                        "properties": { "type": { "type": "string" } }
                      },
                      "Car": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseType" },
                          {
                            "type": "object",
                            "properties": {
                              "doors": { "type": "integer" },
                              "engine": { "type": "string" }
                            }
                          },
                          {
                            "type": "object",
                            "properties": { "brand": { "type": "string" } }
                          }
                        ]
                      },
                      "Bike": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseType" },
                          {
                            "type": "object",
                            "properties": { "gears": { "type": "integer" } }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseType": {
                        "type": "object",
                        "properties": { "type": { "type": "string" } }
                      },
                      "Vehicle": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [ "car" ],
                                    "default": "car",
                                    "description": "Always set to 'car'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "doors": { "type": "integer" },
                                  "engine": { "type": "string" }
                                }
                              },
                              {
                                "type": "object",
                                "properties": { "brand": { "type": "string" } }
                              }
                            ]
                          },
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [ "bike" ],
                                    "default": "bike",
                                    "description": "Always set to 'bike'."
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": { "gears": { "type": "integer" } }
                              }
                            ]
                          }
                        ]
                      },
                      "Car": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [ "car" ],
                                "default": "car",
                                "description": "Always set to 'car'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "doors": { "type": "integer" },
                              "engine": { "type": "string" }
                            }
                          },
                          {
                            "type": "object",
                            "properties": { "brand": { "type": "string" } }
                          }
                        ]
                      },
                      "Bike": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [ "bike" ],
                                "default": "bike",
                                "description": "Always set to 'bike'."
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": { "gears": { "type": "integer" } }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName(
                "First mapped schema adjusted, subsequent mappings added as $ref (three mappings) and unrelated schema untouched")
        void threeMappingsAndUnrelatedPreserved() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": { "schemas": {
                    "Base": {
                      "type": "object",
                      "discriminator": {"propertyName": "dtype", "mapping": {
                        "first": "#/components/schemas/Foo",
                        "second": "#/components/schemas/Bar",
                        "third": "#/components/schemas/Baz"
                      }},
                      "properties": {
                        "dtype": {"type": "string"},
                        "common": {"type": "string"}
                      }
                    },
                    "Foo": {
                      "type": "object",
                      "properties": {
                        "dtype": {"type": "string"},
                        "f": {"type": "integer"}
                      }
                    },
                    "Bar": {
                      "type": "object",
                      "properties": {
                        "dtype": {"type": "string"},
                        "b": {"type": "boolean"}
                      }
                    },
                    "Baz": {
                      "type": "object",
                      "properties": {
                        "dtype": {"type": "string"},
                        "z": {"type": "string"}
                      }
                    },
                    "Unrelated": {
                      "type": "object",
                      "properties": {
                        "u": {"type": "string"}
                      }
                    }
                  }}
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": { "schemas": {
                    "Base": {
                      "type": "object",
                      "oneOf": [
                        {
                          "type": "object",
                          "properties": {
                            "dtype": {
                              "type": "string",
                              "enum": [ "first" ],
                              "default": "first",
                              "description": "Always set to 'first'."
                            },
                            "f": {"type": "integer"}
                          }
                        },
                        {
                          "type": "object",
                          "properties": {
                            "dtype": {
                              "type": "string",
                              "enum": [ "second" ],
                              "default": "second",
                              "description": "Always set to 'second'."
                            },
                            "b": {"type": "boolean"}
                          }
                        },
                        {
                        "type": "object",
                          "properties": {
                            "dtype": {
                              "type": "string",
                              "enum": [ "third" ],
                              "default": "third",
                              "description": "Always set to 'third'."
                            },
                            "z": {"type": "string"}
                          }
                        }
                      ]
                    },
                    "Foo": {
                      "type": "object",
                      "properties": {
                        "dtype": {"type": "string"},
                        "f": {"type": "integer"}
                      }
                    },
                    "Bar": {
                      "type": "object",
                      "properties": {
                        "dtype": {"type": "string"},
                        "b": {"type": "boolean"}
                      }
                    },
                    "Baz": {
                      "type": "object",
                      "properties": {
                        "dtype": {"type": "string"},
                        "z": {"type": "string"}
                      }
                    },
                    "Unrelated": {
                      "type": "object",
                      "properties": {
                        "u": {"type": "string"}
                      }
                    }
                  }}
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    class AnyOfAndOneOfTraversal {
        @Test
        @DisplayName("Discriminator inside schema referenced through anyOf is flattened")
        void anyOfTraversal() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Envelope": {
                        "type": "object",
                        "properties": {
                          "payload": {
                            "anyOf": [
                              { "$ref": "#/components/schemas/Animal" },
                              { "type": "string" }
                            ]
                          }
                        }
                      },
                      "Animal": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "atype",
                          "mapping": { "dog": "#/components/schemas/Dog" }
                        },
                        "properties": { "atype": { "type": "string" } }
                      },
                      "Dog": {
                        "type": "object",
                        "properties": {
                          "atype": { "type": "string" },
                          "bark": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Envelope": {
                        "type": "object",
                        "properties": {
                          "payload": {
                            "anyOf": [
                              { "$ref": "#/components/schemas/Animal" },
                              { "type": "string" }
                            ]
                          }
                        }
                      },
                      "Animal": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "properties": {
                              "atype": {
                                "type": "string",
                                "enum": [ "dog" ],
                                "default": "dog",
                                "description": "Always set to 'dog'."
                              },
                              "bark": { "type": "boolean" }
                            }
                          }
                        ]
                      },
                      "Dog": {
                        "type": "object",
                        "properties": {
                          "atype": { "type": "string" },
                          "bark": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName(
                "Discriminator inside schema referenced through oneOf is flattened without affecting sibling schemas")
        void oneOfTraversal() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Container": {
                        "type": "object",
                        "properties": {
                          "data": {
                            "oneOf": [
                              { "$ref": "#/components/schemas/Msg" },
                              { "type": "integer" }
                            ]
                          }
                        }
                      },
                      "Msg": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "mtype",
                          "mapping": { "text": "#/components/schemas/TextMsg" }
                        },
                        "properties": { "mtype": { "type": "string" } }
                      },
                      "TextMsg": {
                        "type": "object",
                        "properties": {
                          "mtype": { "type": "string" },
                          "body": { "type": "string" }
                        }
                      },
                      "Sibling": {
                        "type": "object",
                        "properties": { "x": { "type": "string" } }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Container": {
                        "type": "object",
                        "properties": {
                          "data": {
                            "oneOf": [
                              { "$ref": "#/components/schemas/Msg" },
                              { "type": "integer" }
                            ]
                          }
                        }
                      },
                      "Msg": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "properties": {
                              "mtype": {
                                "type": "string",
                                "enum": [ "text" ],
                                "default": "text",
                                "description": "Always set to 'text'."
                              },
                              "body": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "TextMsg": {
                        "type": "object",
                        "properties": {
                          "mtype": { "type": "string" },
                          "body": { "type": "string" }
                        }
                      },
                      "Sibling": {
                        "type": "object",
                        "properties": { "x": { "type": "string" } }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    class MultipleDiscriminatorMappingsWithAllOf {

        @Test
        @DisplayName("Discriminator property defined as $ref with complex allOf mappings and required fields")
        void discriminatorPropertyAsRefWithComplexAllOf() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "OrderType": {
                        "type": "string",
                        "enum": ["TYPE_A", "TYPE_B"]
                      },
                      "Origin": {
                        "type": "string",
                        "enum": ["DOMESTIC", "INTERNATIONAL"]
                      },
                      "ResourceType": {
                        "type": "string",
                        "enum": ["LOCAL", "REMOTE"]
                      },
                      "Capability": {
                        "type": "string",
                        "enum": ["READ", "WRITE"]
                      },
                      "BaseRequest": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "type",
                          "mapping": {
                            "TYPE_A": "#/components/schemas/TypeARequest",
                            "TYPE_B": "#/components/schemas/TypeBRequest"
                          }
                        },
                        "properties": {
                          "type": {
                            "$ref": "#/components/schemas/OrderType"
                          }
                        },
                        "required": ["type"]
                      },
                      "TypeARequest": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseRequest" },
                          {
                            "type": "object",
                            "properties": {
                              "code": {
                                "type": "string",
                                "description": "Identifier code."
                              },
                              "origin": {
                                "$ref": "#/components/schemas/Origin"
                              }
                            }
                          }
                        ],
                        "required": ["code", "type"]
                      },
                      "TypeBRequest": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/BaseRequest" },
                          {
                            "type": "object",
                            "properties": {
                              "quantity": {
                                "type": "integer",
                                "format": "int32",
                                "description": "Quantity value.",
                                "maximum": 20,
                                "minimum": 1
                              },
                              "resourceType": {
                                "$ref": "#/components/schemas/ResourceType"
                              },
                              "capabilities": {
                                "type": "array",
                                "description": "Resource capabilities.",
                                "items": {
                                  "$ref": "#/components/schemas/Capability"
                                }
                              },
                              "code": {
                                "type": "string",
                                "description": "Identifier code."
                              }
                            }
                          }
                        ],
                        "required": ["capabilities", "code", "resourceType", "type"]
                      }
                    }
                  }
                }
                """;

            var expectedOutput = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "OrderType": {
                        "type": "string",
                        "enum": ["TYPE_A", "TYPE_B"]
                      },
                      "Origin": {
                        "type": "string",
                        "enum": ["DOMESTIC", "INTERNATIONAL"]
                      },
                      "ResourceType": {
                        "type": "string",
                        "enum": ["LOCAL", "REMOTE"]
                      },
                      "Capability": {
                        "type": "string",
                        "enum": ["READ", "WRITE"]
                      },
                      "BaseRequest": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": ["TYPE_A"],
                                    "default": "TYPE_A",
                                    "description": "Always set to 'TYPE_A'."
                                  }
                                },
                                "required": ["type"]
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "code": {
                                    "type": "string",
                                    "description": "Identifier code."
                                  },
                                  "origin": {
                                    "$ref": "#/components/schemas/Origin"
                                  }
                                }
                              }
                            ],
                            "required": ["code", "type"]
                          },
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": ["TYPE_B"],
                                    "default": "TYPE_B",
                                    "description": "Always set to 'TYPE_B'."
                                  }
                                },
                                "required": ["type"]
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "quantity": {
                                    "type": "integer",
                                    "format": "int32",
                                    "description": "Quantity value.",
                                    "maximum": 20,
                                    "minimum": 1
                                  },
                                  "resourceType": {
                                    "$ref": "#/components/schemas/ResourceType"
                                  },
                                  "capabilities": {
                                    "type": "array",
                                    "description": "Resource capabilities.",
                                    "items": {
                                      "$ref": "#/components/schemas/Capability"
                                    }
                                  },
                                  "code": {
                                    "type": "string",
                                    "description": "Identifier code."
                                  }
                                }
                              }
                            ],
                            "required": ["capabilities", "code", "resourceType", "type"]
                          }
                        ],
                        "required": ["type"]
                      },
                      "TypeARequest": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": ["TYPE_A"],
                                "default": "TYPE_A",
                                "description": "Always set to 'TYPE_A'."
                              }
                            },
                            "required": ["type"]
                          },
                          {
                            "type": "object",
                            "properties": {
                              "code": {
                                "type": "string",
                                "description": "Identifier code."
                              },
                              "origin": {
                                "$ref": "#/components/schemas/Origin"
                              }
                            }
                          }
                        ],
                        "required": ["code", "type"]
                      },
                      "TypeBRequest": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": ["TYPE_B"],
                                "default": "TYPE_B",
                                "description": "Always set to 'TYPE_B'."
                              }
                            },
                            "required": ["type"]
                          },
                          {
                            "type": "object",
                            "properties": {
                              "quantity": {
                                "type": "integer",
                                "format": "int32",
                                "description": "Quantity value.",
                                "maximum": 20,
                                "minimum": 1
                              },
                              "resourceType": {
                                "$ref": "#/components/schemas/ResourceType"
                              },
                              "capabilities": {
                                "type": "array",
                                "description": "Resource capabilities.",
                                "items": {
                                  "$ref": "#/components/schemas/Capability"
                                }
                              },
                              "code": {
                                "type": "string",
                                "description": "Identifier code."
                              }
                            }
                          }
                        ],
                        "required": ["capabilities", "code", "resourceType", "type"]
                      }
                    }
                  }
                }
                """;

            assertFlattened(input, expectedOutput);
        }
    }

    @Nested
    class SingleMappingWithRefProperty {

        @Test
        @DisplayName("Discriminator with property as $ref and single mapping")
        void singleMappingWithRefDiscriminatorProperty() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ActionType": {
                        "type": "string",
                        "enum": ["FORWARD", "REDIRECT"]
                      },
                      "BaseAction": {
                        "type": "object",
                        "description": "Base action configuration.",
                        "discriminator": {
                          "propertyName": "type",
                          "mapping": {
                            "FORWARD": "#/components/schemas/ForwardAction"
                          }
                        },
                        "example": {
                          "type": "FORWARD",
                          "url": "https://example.com",
                          "format": "JSON"
                        },
                        "properties": {
                          "type": {
                            "$ref": "#/components/schemas/ActionType"
                          }
                        }
                      },
                      "ForwardAction": {
                        "type": "object",
                        "properties": {
                          "type": {
                            "$ref": "#/components/schemas/ActionType"
                          },
                          "url": { "type": "string" },
                          "format": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ActionType": {
                        "type": "string",
                        "enum": ["FORWARD", "REDIRECT"]
                      },
                      "BaseAction": {
                        "type": "object",
                        "description": "Base action configuration.",
                        "example": {
                          "type": "FORWARD",
                          "url": "https://example.com",
                          "format": "JSON"
                        },
                        "oneOf": [
                          {
                            "type": "object",
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [ "FORWARD" ],
                                "default": "FORWARD",
                                "description": "Always set to 'FORWARD'."
                              },
                              "url": { "type": "string" },
                              "format": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "ForwardAction": {
                        "type": "object",
                        "properties": {
                          "type": {
                            "$ref": "#/components/schemas/ActionType"
                          },
                          "url": { "type": "string" },
                          "format": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    class DuplicateDiscriminatorPropertyInAllOf {

        @Test
        @DisplayName("Referenced schema with duplicate discriminator - only first is adjusted, second is skipped")
        void referencedSchemaWithDuplicateDiscriminator(CapturedOutput output) throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseMessage": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "messageType",
                          "mapping": {
                            "TEXT": "#/components/schemas/TextMessage"
                          }
                        },
                        "properties": {
                          "messageType": {
                            "type": "string"
                          }
                        },
                        "required": ["messageType"]
                      },
                      "CommonProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Type of the message"
                          },
                          "timestamp": {
                            "type": "string",
                            "format": "date-time"
                          }
                        }
                      },
                      "AdditionalProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Message type identifier"
                          },
                          "sender": {
                            "type": "string"
                          }
                        }
                      },
                      "TextMessage": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/CommonProperties" },
                          { "$ref": "#/components/schemas/AdditionalProperties" }
                        ],
                        "required": ["messageType"]
                      }
                    }
                  }
                }
                """;

            var expectedOutput = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseMessage": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "messageType": {
                                    "type": "string",
                                    "enum": ["TEXT"],
                                    "default": "TEXT",
                                    "description": "Always set to 'TEXT'."
                                  },
                                  "timestamp": {
                                    "type": "string",
                                    "format": "date-time"
                                  }
                                }
                              }
                            ],
                            "required": ["messageType"]
                          }
                        ],
                        "required": ["messageType"]
                      },
                      "CommonProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Type of the message"
                          },
                          "timestamp": {
                            "type": "string",
                            "format": "date-time"
                          }
                        }
                      },
                      "AdditionalProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Message type identifier"
                          },
                          "sender": {
                            "type": "string"
                          }
                        }
                      },
                      "TextMessage": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "enum": ["TEXT"],
                                "default": "TEXT",
                                "description": "Always set to 'TEXT'."
                              },
                              "timestamp": {
                                "type": "string",
                                "format": "date-time"
                              }
                            }
                          }
                        ],
                        "required": ["messageType"]
                      }
                    }
                  }
                }
                """;

            assertFlattened(input, expectedOutput);

            then(output.getOut())
                    .contains("Multiple schemas define the same discriminator property 'messageType'. "
                            + "AllOf component 'AdditionalProperties' will be skipped as the property has already been adjusted during schema TextMessage processing. "
                            + "Skipped schema had 2 properties.");
        }

        @Test
        @DisplayName("Inline schema with duplicate discriminator - only first is adjusted, second is skipped")
        void inlineSchemaWithDuplicateDiscriminator(CapturedOutput output) throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseMessage": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "messageType",
                          "mapping": {
                            "TEXT": "#/components/schemas/TextMessage"
                          }
                        },
                        "properties": {
                          "messageType": {
                            "type": "string"
                          }
                        },
                        "required": ["messageType"]
                      },
                      "TextMessage": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "description": "Type of the message"
                              },
                              "timestamp": {
                                "type": "string",
                                "format": "date-time"
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "description": "Duplicate message type"
                              },
                              "sender": {
                                "type": "string"
                              }
                            }
                          }
                        ],
                        "required": ["messageType"]
                      }
                    }
                  }
                }
                """;

            var expectedOutput = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseMessage": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "messageType": {
                                    "type": "string",
                                    "enum": ["TEXT"],
                                    "default": "TEXT",
                                    "description": "Always set to 'TEXT'."
                                  },
                                  "timestamp": {
                                    "type": "string",
                                    "format": "date-time"
                                  }
                                }
                              }
                            ],
                            "required": ["messageType"]
                          }
                        ],
                        "required": ["messageType"]
                      },
                      "TextMessage": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "enum": ["TEXT"],
                                "default": "TEXT",
                                "description": "Always set to 'TEXT'."
                              },
                              "timestamp": {
                                "type": "string",
                                "format": "date-time"
                              }
                            }
                          }
                        ],
                        "required": ["messageType"]
                      }
                    }
                  }
                }
                """;

            assertFlattened(input, expectedOutput);

            then(output.getOut())
                    .contains("Multiple schemas define the same discriminator property 'messageType'. "
                            + "Inline allOf schema will be skipped as the property has already been adjusted during schema TextMessage processing. "
                            + "Skipped schema had 2 properties.");
        }

        @Test
        @DisplayName(
                "Mixed ref and inline schemas with duplicate discriminator - loop continues and processes all schemas")
        void mixedRefAndInlineSchemasWithDuplicateDiscriminator(CapturedOutput output) throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseMessage": {
                        "type": "object",
                        "discriminator": {
                          "propertyName": "messageType",
                          "mapping": {
                            "TEXT": "#/components/schemas/TextMessage"
                          }
                        },
                        "properties": {
                          "messageType": {
                            "type": "string"
                          }
                        },
                        "required": ["messageType"]
                      },
                      "CommonProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Type of the message"
                          },
                          "timestamp": {
                            "type": "string",
                            "format": "date-time"
                          }
                        }
                      },
                      "AdditionalProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Message type identifier"
                          },
                          "sender": {
                            "type": "string"
                          }
                        }
                      },
                      "TextMessage": {
                        "type": "object",
                        "allOf": [
                          { "$ref": "#/components/schemas/CommonProperties" },
                          {
                            "type": "object",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "description": "Duplicate inline discriminator"
                              },
                              "priority": {
                                "type": "integer"
                              }
                            }
                          },
                          { "$ref": "#/components/schemas/AdditionalProperties" },
                          {
                            "type": "object",
                            "properties": {
                              "text": {
                                "type": "string",
                                "description": "Message text content"
                              }
                            }
                          }
                        ],
                        "required": ["messageType", "text"]
                      }
                    }
                  }
                }
                """;

            var expectedOutput = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "BaseMessage": {
                        "type": "object",
                        "oneOf": [
                          {
                            "type": "object",
                            "allOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "messageType": {
                                    "type": "string",
                                    "enum": ["TEXT"],
                                    "default": "TEXT",
                                    "description": "Always set to 'TEXT'."
                                  },
                                  "timestamp": {
                                    "type": "string",
                                    "format": "date-time"
                                  }
                                }
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "text": {
                                    "type": "string",
                                    "description": "Message text content"
                                  }
                                }
                              }
                            ],
                            "required": ["messageType", "text"]
                          }
                        ],
                        "required": ["messageType"]
                      },
                      "CommonProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Type of the message"
                          },
                          "timestamp": {
                            "type": "string",
                            "format": "date-time"
                          }
                        }
                      },
                      "AdditionalProperties": {
                        "type": "object",
                        "properties": {
                          "messageType": {
                            "type": "string",
                            "description": "Message type identifier"
                          },
                          "sender": {
                            "type": "string"
                          }
                        }
                      },
                      "TextMessage": {
                        "type": "object",
                        "allOf": [
                          {
                            "type": "object",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "enum": ["TEXT"],
                                "default": "TEXT",
                                "description": "Always set to 'TEXT'."
                              },
                              "timestamp": {
                                "type": "string",
                                "format": "date-time"
                              }
                            }
                          },
                          {
                            "type": "object",
                            "properties": {
                              "text": {
                                "type": "string",
                                "description": "Message text content"
                              }
                            }
                          }
                        ],
                        "required": ["messageType", "text"]
                      }
                    }
                  }
                }
                """;

            assertFlattened(input, expectedOutput);

            then(output.getOut())
                    .contains("Multiple schemas define the same discriminator property 'messageType'. "
                            + "Inline allOf schema will be skipped as the property has already been adjusted during schema TextMessage processing. "
                            + "Skipped schema had 2 properties.")
                    .contains("Multiple schemas define the same discriminator property 'messageType'. "
                            + "AllOf component 'AdditionalProperties' will be skipped as the property has already been adjusted during schema TextMessage processing. "
                            + "Skipped schema had 2 properties.");
        }
    }

    @Nested
    class OpenApi31SchemaProperties {

        @Test
        @DisplayName("Discriminator inside prefixItems schema is flattened")
        void prefixItemsDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "TupleContainer": {
                        "type": "array",
                        "prefixItems": [
                          {
                            "type": "object",
                            "discriminator": { "propertyName": "type", "mapping": { "item": "#/components/schemas/Item" } },
                            "properties": { "type": { "type": "string" } }
                          }
                        ]
                      },
                      "Item": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "value": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "TupleContainer": {
                        "type": "array",
                        "prefixItems": [
                          {
                            "type": "object",
                            "oneOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [ "item" ],
                                    "default": "item",
                                    "description": "Always set to 'item'."
                                  },
                                  "value": { "type": "string" }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      "Item": {
                        "type": "object",
                        "properties": {
                          "type": { "type": "string" },
                          "value": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside items schema is flattened")
        void itemsDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ArrayContainer": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "discriminator": { "propertyName": "kind", "mapping": { "element": "#/components/schemas/Element" } },
                          "properties": { "kind": { "type": "string" } }
                        }
                      },
                      "Element": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string" },
                          "data": { "type": "integer" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ArrayContainer": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "kind": {
                                  "type": "string",
                                  "enum": [ "element" ],
                                  "default": "element",
                                  "description": "Always set to 'element'."
                                },
                                "data": { "type": "integer" }
                              }
                            }
                          ]
                        }
                      },
                      "Element": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string" },
                          "data": { "type": "integer" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside unevaluatedItems schema is flattened")
        void unevaluatedItemsDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ComplexArray": {
                        "type": "array",
                        "unevaluatedItems": {
                          "type": "object",
                          "discriminator": { "propertyName": "mode", "mapping": { "fallback": "#/components/schemas/Fallback" } },
                          "properties": { "mode": { "type": "string" } }
                        }
                      },
                      "Fallback": {
                        "type": "object",
                        "properties": {
                          "mode": { "type": "string" },
                          "fallbackValue": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ComplexArray": {
                        "type": "array",
                        "unevaluatedItems": {
                          "type": "object",
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "mode": {
                                  "type": "string",
                                  "enum": [ "fallback" ],
                                  "default": "fallback",
                                  "description": "Always set to 'fallback'."
                                },
                                "fallbackValue": { "type": "string" }
                              }
                            }
                          ]
                        }
                      },
                      "Fallback": {
                        "type": "object",
                        "properties": {
                          "mode": { "type": "string" },
                          "fallbackValue": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside contains schema is flattened")
        void containsDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "SearchableArray": {
                        "type": "array",
                        "contains": {
                          "type": "object",
                          "discriminator": { "propertyName": "searchType", "mapping": { "match": "#/components/schemas/MatchItem" } },
                          "properties": { "searchType": { "type": "string" } }
                        }
                      },
                      "MatchItem": {
                        "type": "object",
                        "properties": {
                          "searchType": { "type": "string" },
                          "pattern": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "SearchableArray": {
                        "type": "array",
                        "contains": {
                          "type": "object",
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "searchType": {
                                  "type": "string",
                                  "enum": [ "match" ],
                                  "default": "match",
                                  "description": "Always set to 'match'."
                                },
                                "pattern": { "type": "string" }
                              }
                            }
                          ]
                        }
                      },
                      "MatchItem": {
                        "type": "object",
                        "properties": {
                          "searchType": { "type": "string" },
                          "pattern": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside dependentSchemas is flattened")
        void dependentSchemasDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ConditionalObject": {
                        "type": "object",
                        "properties": {
                          "hasConfig": { "type": "boolean" }
                        },
                        "dependentSchemas": {
                          "hasConfig": {
                            "type": "object",
                            "discriminator": { "propertyName": "configType", "mapping": { "advanced": "#/components/schemas/AdvancedConfig" } },
                            "properties": { "configType": { "type": "string" } }
                          }
                        }
                      },
                      "AdvancedConfig": {
                        "type": "object",
                        "properties": {
                          "configType": { "type": "string" },
                          "settings": { "type": "object" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ConditionalObject": {
                        "type": "object",
                        "properties": {
                          "hasConfig": { "type": "boolean" }
                        },
                        "dependentSchemas": {
                          "hasConfig": {
                            "type": "object",
                            "oneOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "configType": {
                                    "type": "string",
                                    "enum": [ "advanced" ],
                                    "default": "advanced",
                                    "description": "Always set to 'advanced'."
                                  },
                                  "settings": { "type": "object" }
                                }
                              }
                            ]
                          }
                        }
                      },
                      "AdvancedConfig": {
                        "type": "object",
                        "properties": {
                          "configType": { "type": "string" },
                          "settings": { "type": "object" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside else schema is flattened")
        void elseDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "IfThenElse": {
                        "type": "object",
                        "if": { "properties": { "type": { "const": "premium" } } },
                        "then": { "properties": { "price": { "type": "number" } } },
                        "else": {
                          "type": "object",
                          "discriminator": { "propertyName": "fallbackType", "mapping": { "basic": "#/components/schemas/BasicType" } },
                          "properties": { "fallbackType": { "type": "string" } }
                        }
                      },
                      "BasicType": {
                        "type": "object",
                        "properties": {
                          "fallbackType": { "type": "string" },
                          "simple": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "IfThenElse": {
                        "type": "object",
                        "if": { "properties": { "type": { "const": "premium" } } },
                        "then": { "properties": { "price": { "type": "number" } } },
                        "else": {
                          "type": "object",
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "fallbackType": {
                                  "type": "string",
                                  "enum": [ "basic" ],
                                  "default": "basic",
                                  "description": "Always set to 'basic'."
                                },
                                "simple": { "type": "boolean" }
                              }
                            }
                          ]
                        }
                      },
                      "BasicType": {
                        "type": "object",
                        "properties": {
                          "fallbackType": { "type": "string" },
                          "simple": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside not schema is flattened")
        void notDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "NotWrapper": {
                        "type": "object",
                        "not": {
                          "type": "object",
                          "discriminator": { "propertyName": "forbidden", "mapping": { "denied": "#/components/schemas/Denied" } },
                          "properties": { "forbidden": { "type": "string" } }
                        }
                      },
                      "Denied": {
                        "type": "object",
                        "properties": {
                          "forbidden": { "type": "string" },
                          "reason": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "NotWrapper": {
                        "type": "object",
                        "not": {
                          "type": "object",
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "forbidden": {
                                  "type": "string",
                                  "enum": [ "denied" ],
                                  "default": "denied",
                                  "description": "Always set to 'denied'."
                                },
                                "reason": { "type": "string" }
                              }
                            }
                          ]
                        }
                      },
                      "Denied": {
                        "type": "object",
                        "properties": {
                          "forbidden": { "type": "string" },
                          "reason": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Discriminator inside contentSchema is flattened")
        void contentSchemaDiscriminator() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "StringWithContent": {
                        "type": "string",
                        "contentEncoding": "base64",
                        "contentMediaType": "application/json",
                        "contentSchema": {
                          "type": "object",
                          "discriminator": { "propertyName": "contentType", "mapping": { "data": "#/components/schemas/DataContent" } },
                          "properties": { "contentType": { "type": "string" } }
                        }
                      },
                      "DataContent": {
                        "type": "object",
                        "properties": {
                          "contentType": { "type": "string" },
                          "payload": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "StringWithContent": {
                        "type": "string",
                        "contentEncoding": "base64",
                        "contentMediaType": "application/json",
                        "contentSchema": {
                          "type": "object",
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "contentType": {
                                  "type": "string",
                                  "enum": [ "data" ],
                                  "default": "data",
                                  "description": "Always set to 'data'."
                                },
                                "payload": { "type": "string" }
                              }
                            }
                          ]
                        }
                      },
                      "DataContent": {
                        "type": "object",
                        "properties": {
                          "contentType": { "type": "string" },
                          "payload": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("Multiple OpenAPI 3.1 properties with discriminators are all flattened")
        void multipleOpenApi31PropertiesWithDiscriminators() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ComplexSchema": {
                        "type": "object",
                        "properties": {
                          "arrayProp": {
                            "type": "array",
                            "items": {
                              "discriminator": { "propertyName": "itemType", "mapping": { "simple": "#/components/schemas/SimpleItem" } },
                              "properties": { "itemType": { "type": "string" } }
                            }
                          }
                        },
                        "not": {
                          "discriminator": { "propertyName": "notType", "mapping": { "forbidden": "#/components/schemas/ForbiddenType" } },
                          "properties": { "notType": { "type": "string" } }
                        }
                      },
                      "SimpleItem": {
                        "type": "object",
                        "properties": {
                          "itemType": { "type": "string" },
                          "value": { "type": "string" }
                        }
                      },
                      "ForbiddenType": {
                        "type": "object",
                        "properties": {
                          "notType": { "type": "string" },
                          "illegal": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "ComplexSchema": {
                        "type": "object",
                        "properties": {
                          "arrayProp": {
                            "type": "array",
                            "items": {
                              "oneOf": [
                                {
                                  "type": "object",
                                  "properties": {
                                    "itemType": {
                                      "type": "string",
                                      "enum": [ "simple" ],
                                      "default": "simple",
                                      "description": "Always set to 'simple'."
                                    },
                                    "value": { "type": "string" }
                                  }
                                }
                              ]
                            }
                          }
                        },
                        "not": {
                          "oneOf": [
                            {
                              "type": "object",
                              "properties": {
                                "notType": {
                                  "type": "string",
                                  "enum": [ "forbidden" ],
                                  "default": "forbidden",
                                  "description": "Always set to 'forbidden'."
                                },
                                "illegal": { "type": "boolean" }
                              }
                            }
                          ]
                        }
                      },
                      "SimpleItem": {
                        "type": "object",
                        "properties": {
                          "itemType": { "type": "string" },
                          "value": { "type": "string" }
                        }
                      },
                      "ForbiddenType": {
                        "type": "object",
                        "properties": {
                          "notType": { "type": "string" },
                          "illegal": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """;
            assertFlattened(input, expected);
        }
    }

    @Nested
    @DisplayName("Same description as parent schema")
    class SameDescriptionAsParent {
        @Test
        @DisplayName("When adjusted schema has same description as parent, use discriminator value as description")
        void replacesDescriptionWithDiscriminatorValueWhenMatchesParent() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Message": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "properties": {
                          "messageType": { "type": "string" }
                        },
                        "discriminator": {
                          "propertyName": "messageType",
                          "mapping": {
                            "text": "#/components/schemas/TextMessage",
                            "image": "#/components/schemas/ImageMessage"
                          }
                        }
                      },
                      "TextMessage": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "properties": {
                          "messageType": { "type": "string" },
                          "text": { "type": "string" }
                        }
                      },
                      "ImageMessage": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "properties": {
                          "messageType": { "type": "string" },
                          "imageUrl": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;

            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Message": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "oneOf": [
                          {
                            "type": "object",
                            "description": "text",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "enum": [ "text" ],
                                "default": "text",
                                "description": "Always set to 'text'."
                              },
                              "text": { "type": "string" }
                            }
                          },
                          {
                            "type": "object",
                            "description": "image",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "enum": [ "image" ],
                                "default": "image",
                                "description": "Always set to 'image'."
                              },
                              "imageUrl": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "TextMessage": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "properties": {
                          "messageType": { "type": "string" },
                          "text": { "type": "string" }
                        }
                      },
                      "ImageMessage": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "properties": {
                          "messageType": { "type": "string" },
                          "imageUrl": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;

            assertFlattened(input, expected);
        }

        @Test
        @DisplayName("When adjusted schema has different description from parent, keeps original description")
        void keepsOriginalDescriptionWhenDifferentFromParent() throws Exception {
            var input = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Message": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "properties": {
                          "messageType": { "type": "string" }
                        },
                        "discriminator": {
                          "propertyName": "messageType",
                          "mapping": {
                            "text": "#/components/schemas/TextMessage"
                          }
                        }
                      },
                      "TextMessage": {
                        "type": "object",
                        "description": "A text message with content",
                        "properties": {
                          "messageType": { "type": "string" },
                          "text": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;

            var expected = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "API", "version": "1" },
                  "paths": { },
                  "servers": [ { "url": "/" } ],
                  "components": {
                    "schemas": {
                      "Message": {
                        "type": "object",
                        "description": "A message that can be sent",
                        "oneOf": [
                          {
                            "type": "object",
                            "description": "A text message with content",
                            "properties": {
                              "messageType": {
                                "type": "string",
                                "enum": [ "text" ],
                                "default": "text",
                                "description": "Always set to 'text'."
                              },
                              "text": { "type": "string" }
                            }
                          }
                        ]
                      },
                      "TextMessage": {
                        "type": "object",
                        "description": "A text message with content",
                        "properties": {
                          "messageType": { "type": "string" },
                          "text": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;

            assertFlattened(input, expected);
        }
    }
}
