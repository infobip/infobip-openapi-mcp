# OpenAPI Specialist Agent Memory

## Key Architectural Facts

- Default wrapper keys: `_params` for parameters, `_body` for request body (configurable via `OpenApiMcpProperties.Tools.Schema`)
- `SUPPORTED_PARAMETER_TYPES` = `{"query", "path", "header", "cookie"}` (from `Spec.java`)
- `SUPPORTED_MEDIA_TYPE` = `application/json` (from `DecomposedRequestData`)
- `InputExampleComposer` precedence per parameter: `parameter.examples` (first `.value` non-null) > `parameter.example` > `parameter.schema.example`
- `InputExampleComposer` precedence for body: `mediaType.examples` (first `.value` non-null) > `mediaType.example` > `mediaType.schema.example`
- Parameters with no resolvable example are silently omitted from the result map
- `extractParameterExamples` returns null (not empty map) when zero parameters yield an example

## Test Fixture Locations

- Core unit test fixtures: `infobip-openapi-mcp-core/src/test/resources/openapi/`
- Spring Boot starter integration test fixtures: `infobip-openapi-mcp-spring-boot-starter/src/test/resources/openapi/`

## OAS 3.1 Parameter Authoring Rule

Every parameter object requires exactly one of `schema` or `content` (enforced by the OAS 3.1 JSON Schema via `oneOf`).
When testing `parameter.example` or `parameter.examples`, always include a `schema` field alongside them — the `example`/`examples` fields are siblings to `schema`, not inside it.

## Combination Rules (InputExampleComposer)

- Only params with examples → flat `{"paramName": value, ...}`
- Only body with example → body example value directly (no wrapper)
- Both params and body → `{"_params": {...}, "_body": <bodyExample>}`
- Neither → `null` (nothing appended to description)

## Details on `examples` Map Ordering

`extractFirstExampleValue` uses `.values().stream().filter(e -> e.getValue() != null).findFirst()`.
In JSON, object key order is insertion order for LinkedHashMap (the swagger-parser preserves declaration order).
Fixture authors should place the intended "first" example entry first in the JSON object.
