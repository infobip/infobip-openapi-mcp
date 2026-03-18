# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Bugfix

- Reverted the changes that removed the jaxb dependency in 0.1.10 release.

## 0.1.10

### Added

- MCP tool annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`) are now automatically inferred from the HTTP method of each API operation and included in tool metadata. Sensible defaults are applied (e.g., GET tools are marked as read-only and idempotent, DELETE tools as destructive). Annotations can be overridden per-operation using the `x-mcp-annotations` vendor extension on the OpenAPI Operation object, or globally per tool name using `infobip.openapi.mcp.tools.annotations.<tool-name>.*` configuration properties. Override precedence is: HTTP method defaults < vendor extension < YAML properties.

### Changed

- Improve test reliability
 
### Removed

- Remove explicit jaxb dependency, it is a compile dependency inside of swagger-parser

## 0.1.9

### Added

- Tool descriptions can now include request examples rendered as Markdown JSON code blocks, helping LLMs understand how to call the tool. When a request body defines multiple named examples (via the `examples` map), all variants are rendered — each with an optional heading derived from the example's `summary` and an optional description paragraph. The feature is controlled by `infobip.openapi.mcp.tools.examples-mode`, which accepts three values: `SKIP` (default, no examples), `ALL` (all examples from the spec), or `ANNOTATED` (only examples annotated with `x-mcp-example: true` on the OpenAPI `Example` Object — ideal for selectively surfacing the most relevant examples without cluttering tool descriptions).

## 0.1.8

### Added

- Configuration for working with Claude Code on the project.

### Changed

- Upgraded versions of dependencies (notably: Spring AI from `1.1.0` to `1.1.2`, and Jackson from `2.19.4` to `2.21.1`)

## 0.1.7

### Changed

- Live reload now also refreshes the scopes.

## 0.1.6

### Changed

- Live reload now terminates the retry loop early on the first successful reload, regardless of whether tools changed. Retries only occur on failure.
- Live reload retry delay changed from fixed 1-second to exponential backoff between attempts.
- Live reload skips OpenAPI filtering and resolution when the specification version has not changed, avoiding unnecessary processing.

## 0.1.5

### Added

- Live reload feature for automatic OpenAPI specification refresh at runtime with client notifications on tool changes.
- Prometheus metrics for live reload: `com.infobip.openapi.live_reload.executions` and `com.infobip.openapi.live_reload.duration`.
- Configuration properties for live reload: `enabled`, `cron-expression`, and `max-retries`.e

## 0.1.4

### Added

- A new step in the release workflow that automatically updates references to framework version in `README.md`
- `./examples/infobip-sms-mock-mcp` project that demonstrates the recently introduced mock tool mode.
- Enhanced discriminator flattening to detect and skip duplicate discriminator properties in allOf schemas. When multiple schemas define the same discriminator property, only the first occurrence is adjusted
  and subsequent schemas are skipped. Warning logs now include the number of properties in skipped schemas to help identify potential data loss.

### Changed

- Upgraded framework version in the open-meteo-mcp example to the latest release.
- Updated `infobip.openapi.mcp.api-base-url` to accept either absolute URLs or 0-indexed integer values to select the appropriate server from OpenAPI specification.
- Discriminator flattening now replaces adjusted schema descriptions with discriminator property values when the original description matches the parent schema description. This is common in OpenAPI specifications generated from code where polymorphic types share the same base description.
- Removed default values from discriminator properties during discriminator flattening. The discriminator property values are required and should be explicitly provided rather than populated by defaults.

### Fixed

- Removed discriminators from adjusted schemas during discriminator flattening. Once a schema is adjusted to support only a single enum value, the discriminator becomes obsolete. Removed visited schema tracking that was causing discriminator leakage.

## 0.1.3

### Added

- `./examples` directory with a demo app that exposes [Open-Meteo API](https://open-meteo.com/en/docs) as MCP server.
- Added SonarQube Maven plugin (version 5.5.0.6356) for code quality analysis.
- Introduced the `com.infobip.openapi.mcp.openapi.tool.ToolCallFilter` abstraction for customizing tool call behavior.
- Added mock mode in which MCP server responds to tool calls by returning mocks based on examples from OpenAPI spec.

### Fixed

- Updated documentation of `infobip.openapi.mcp.tools.naming.strategy` parameter to correctly document default value is `SANITIZED_OPERATION_ID`.

## 0.1.2

### Added

- Added scope challenge handling to be in compliance with MCP 2025-11-25 authorization specification, section [10.1](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization#runtime-insufficient-scope-errors).

### Changed

- Changed `infobip.openapi.mcp.security.auth.oauth.scope-discovery.enabled` to `true` by default.

## 0.1.1

### Added

- Added pom metadata required for public maven release

## 0.1.0

### Added

- Mapping OpenAPI specification to MCP tools
  - Multiple tool naming strategies (OperationId, SanitizedOperationId, Endpoint) with length trimming support
  - OpenAPI filter system for programmatic specification customization
  - Discriminator resolution for polymorphic models to ensure JSON Schema compatibility
- Translating MCP tool calls to API requests
  - OpenAPI to MCP tool mapping with automatic schema adaptation for path, query, and body parameters
  - API request enricher framework for customizing HTTP requests to downstream APIs
  - JSON double serialization mitigation for handling malformed LLM outputs
- Authentication and OAuth
  - Custom auth types, such as API key, are supported via configurable authentication API endpoint
  - OAuth authentication support with automatic authorization server discovery 
  - OAuth scope discovery from OpenAPI specifications with minimal scope calculation algorithms
- MCP transports and Spring Boot integration
  - Support for stdio, SSE and streamable HTTP MCP transport protocols
  - Spring Boot auto-configuration with externalized configuration properties
