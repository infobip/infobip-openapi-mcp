# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**infobip-openapi-mcp** is an open-source Java framework for exposing OpenAPI-documented HTTP APIs as MCP (Model Context
Protocol) servers. It is built on Spring AI and Spring Boot, and is published to Maven Central.

## Build & Development Commands

Project uses Java 21. Make sure that local tooling supports this version. **DO NOT** attempt to downgrade the source
code to earlier Java versions. Look for newer JKD instead, for example using the SDKMAN version manager.

```bash
# Build the project (runs tests + spotless format check)
mvn verify

# Build skipping tests
mvn package -DskipTests

# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl infobip-openapi-mcp-core
mvn test -pl infobip-openapi-mcp-spring-boot-starter -am

# Run a single test class
mvn test -pl infobip-openapi-mcp-core -Dtest=DiscriminatorFlattenerTest
mvn test -pl infobip-openapi-mcp-spring-boot-starter -am -Dtest=ToolCallIntegrationTest

# Apply code formatting (Palantir Java Format via Spotless)
mvn spotless:apply

# Check formatting without applying
mvn spotless:check

# Install git hooks (runs spotless:apply on pre-commit)
mvn install -Pgit-hook

# Check version of Java that is used by maven
mvn --version

# Check available Java versions with sdkman
PAGER=cat sdk list java | grep -E 'installed|local'

# Pick identifier of installed Java version equal or greater than 21 (for example 25-tem) and enable it with sdkman
sdk use java <identifier>
```

**ALWAYS** do this after completing any coding task:

- Run `mvn spotless:apply` as the final step before presenting results.
- Update `CHANGELOG.md` under the `[Unreleased]` section using
  the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format (`Added`, `Changed`,  `Fixed`, `Removed`).
  Write entries from a user perspective — describe the feature and its value, not the classes or internal mechanics
  behind it. It is fine to mention configuration properties needed to enable or customize a feature, or java interfaces
  that users can implement such as `OpenApiFilter` or `ApiRequestEnricher`. Avoid class names, method names, test names,
  and other implementation details.
- If you added or changed an external configuration property, add or update its row in the properties table in
  `README.md`.
- Check and update `CLAUDE.md` to reflect the new state of the project.

## Module Structure

This is a two-module Maven project:

- **`infobip-openapi-mcp-core`** — Framework core logic. No Spring Boot autoconfiguration, suitable as a library
  dependency.
- **`infobip-openapi-mcp-spring-boot-starter`** — Spring Boot autoconfiguration that wires the core beans. This is what
  users add to their `pom.xml`.

## Architecture

The framework follows this startup flow:

1. `OpenApiRegistry` reads and caches the OpenAPI spec from `infobip.openapi.mcp.open-api-url`
2. `OpenApiFilterChain` applies `OpenApiFilter` beans (e.g., `DiscriminatorFlattener`, `PatternPropertyRemover`) to
   transform the spec
3. `ToolRegistry` converts each API operation into a `RegisteredTool` using `InputSchemaComposer`,
   `InputExampleComposer`, `ToolAnnotationResolver`, and the configured `NamingStrategy`
4. Tools are registered with the Spring AI MCP server (SSE, Streamable HTTP, Stateless HTTP, or stdio transport)

**Runtime tool call flow:**
`ToolSpecBuilder` → `ToolCallFilterChain` (ordered `ToolCallFilter` beans) → `RegisteredTool` (lowest precedence, makes
HTTP call via `ToolHandler`) → optional `JsonDoubleSerializationCorrector` retry logic

### Key Extension Points

| Interface            | Purpose                                                                                                                  |
|----------------------|--------------------------------------------------------------------------------------------------------------------------|
| `OpenApiFilter`           | Transform the OpenAPI spec before tool metadata is built; disable via `infobip.openapi.mcp.filters.[filter-name]: false` |
| `ApiRequestEnricher`      | Modify HTTP requests to the downstream API (headers, metadata); failures are swallowed                                   |
| `ToolCallFilter`          | Intercept tool calls; can abort the chain unlike enrichers                                                               |
| `NamingStrategy`          | Custom tool name generation; replace the default bean                                                                    |
| `ErrorModelProvider`      | Custom error response format returned to MCP clients                                                                     |
| `AuthorizationExtractor`  | Supply credentials from any source (HTTP header, vault, env, etc.); replace the default bean                             |

Important: filters, enrichers, strategies and providers can be implemented by application code, which is outside the
framework. You will not see those implementations in this project's source code. This is the supported way to extend and
customize framework functionality. Take **extra care** to maintain backwards compatibility with unseen application code.

### Authentication

Authentication is handled by `InitialAuthenticationFilter`. When `infobip.openapi.mcp.security.auth.enabled: true`,
every MCP interaction delegates credential validation to the endpoint at `infobip.openapi.mcp.security.auth.auth-url`.
OAuth support (`OAuthConfiguration`, `OAuthController`) proxies `/.well-known` endpoints to the authorization server.

### Schema Transformation

`InputSchemaComposer` merges OpenAPI path/query parameters and request body into a single MCP tool input JSON schema.
When both exist, parameters are wrapped under `_params` key and the request body under `_body` (configurable).

`InputExampleComposer` extracts request examples from OpenAPI parameters and request bodies (using precedence:
`examples` map > `example` field > `schema.example`) and composes them into a single example object matching
`InputSchemaComposer`'s combination rules. `ToolRegistry` appends the result as a Markdown JSON code block to tool
descriptions.

`DiscriminatorFlattener` resolves OpenAPI discriminator patterns into JSON Schema–compatible `oneOf`/`allOf` structures
since MCP does not support OpenAPI discriminators natively.

`ToolAnnotationResolver` infers MCP tool annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`,
`openWorldHint`) from HTTP method semantics, then merges overrides from `x-mcp-annotations` vendor
extension on the Operation and from YAML config properties (`infobip.openapi.mcp.tools.annotations.<tool-name>.*`).

## Code Style

- **Formatter**: Palantir Java Format (enforced by Spotless on every build and pre-commit hook, run `mvn spotless:apply`
  after code changes!)
- **Null annotations**: JSpecify (`@Nullable`, `@NonNull`) — see `package-info.java` files in enricher package (for
  every new package you create put `package-info.java` into it and annotate the package with `@NullMarked`)
- **Java version**: 21 (records, pattern matching, text blocks are in use throughout)
- **Async server is explicitly blocked**: The autoconfiguration throws `BeanCreationNotAllowedException` if
  `spring.ai.mcp.server.type=ASYNC` to enforce sync-only usage
- **Comments**: Javadoc is welcome on public classes and public methods. Inside test method bodies, `// Given`,
  `// When`, `// Then` section markers are fine. Do **not** use decorative separator blocks between methods (e.g.
  `// ---... SectionName ...---`); let the method names speak for themselves
- **swagger-models fluent API**: Prefer the fluent builder pattern over setters when constructing OpenAPI model objects.
  All swagger-models 2.x classes expose property-named fluent methods that return `this` — use them instead of
  `setXxx(...)` calls. For example: `new OpenAPI().specVersion(V31).info(...).components(...).paths(...)` instead of
  `openApi.setSpecVersion(V31); openApi.setInfo(...)`
- **List access**: Prefer `list.getFirst()` over `list.get(0)` when accessing the first element of a list.
- **Collection literals**: Prefer `Map.of(...)` and `List.of(...)` over creating a mutable instance and calling
  `put`/`add` repeatedly, when all elements are known upfront. Do **not** use these when: (a) the collection must
  remain mutable after construction, (b) insertion order must be preserved (`Map.of` is unordered — use
  `LinkedHashMap` instead), or (c) elements are accumulated in a loop/stream.
- **Assertion style**: When validating an object or a list of objects in tests, construct the expected instance(s) and
  assert using `then(actual).usingRecursiveComparison().isEqualTo(expected)` instead of asserting each property
  individually. For a collections prefer AssertJ fluent assertions, such as `containsExactly`.
- **String manipulation**: Within a single class, use one consistent approach — either `+` concatenation or
  `StringBuilder` — do not mix both. Choose whichever fits the class's dominant use case: prefer `StringBuilder` when
  the class contains any method that builds strings conditionally or in a loop; prefer `+` concatenation in classes
  where all string building is simple and unconditional. Assigning a plain variable (`x = someString`) does not count as
  string manipulation and does not influence the choice.

## Testing Conventions

- **Unit tests** live in `infobip-openapi-mcp-core/src/test/`
- **Integration tests** live in `infobip-openapi-mcp-spring-boot-starter/src/test/` under the `integration` package
- Integration tests use **WireMock** to stub the downstream API and the OpenAPI spec endpoint
- `IntegrationTestBase` (stateful) and transport-specific subclasses wire up a full Spring Boot context with
  `TestApplication`
- Test OpenAPI specs are in `src/test/resources/openapi/` (both modules)
- Integration test profiles (`application-integration.yml`, `application-test-http.yml`, etc.) configure which transport
  and OpenAPI spec to use per test class
- `@DirtiesContext` is used on integration tests that reload tools to ensure a clean Spring context per test
- Tests use given-when-then structure, and follow F.I.R.S.T. principles (fast, isolated, repeatable, self-validating,
  thorough)
- In case a lot of setup code is repeated between test methods use parameterized tests

## Examples

The `examples/` directory contains two standalone Spring Boot applications that use this framework:

- `infobip-sms-mock-mcp/` — Mock SMS MCP server using Infobip API spec
- `open-meteo-mcp/` — Real MCP server calling the Open-Meteo weather API

These are not part of the main Maven build (not listed as modules in the root `pom.xml`).