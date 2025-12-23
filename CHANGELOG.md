# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `./examples` directory with a demo app that exposes [Open-Meteo API](https://open-meteo.com/en/docs) as MCP server.
- Added SonarQube Maven plugin (version 5.5.0.6356) for code quality analysis.

### Fixed

- Updated documentation of `infobip.openapi.mcp.tools.naming.strategy` parameter to correctly document default value is `SANITIZED_OPERATION_ID`.

## 0.1.2

### Added

- Added scope challenge handling to be in compliance with MCP 2025-11-25 authorization specification, section [10.1](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization#runtime-insufficient-scope-errors).
- Introduced the `com.infobip.openapi.mcp.openapi.tool.ToolCallFilter` abstraction for customizing tool call behavior.
- Added mock mode in which MCP server responds to tool calls by returning mocks based on examples from OpenAPI spec

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
