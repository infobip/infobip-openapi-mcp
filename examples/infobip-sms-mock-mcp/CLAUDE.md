# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A minimal example Spring Boot application that uses `infobip-openapi-mcp-spring-boot-starter` to expose Infobip's SMS
API as a mock MCP server over Streamable HTTP. Tool calls return OpenAPI spec example responses instead of hitting the
real API, making it useful for testing MCP clients without production credentials.

The entire application is a single `Application.java` class and `application.yaml`; all MCP logic comes from the
framework dependency.

## Build & Run

This project is **not** part of the parent Maven build. Build and run via Docker only:

```bash
# Build Docker image (from this directory)
docker build -t infobip-sms-mock-mcp .

# Run on localhost:8080
docker run --rm -ti -p 8080:8080 infobip-sms-mock-mcp:latest
```

The server uses Java 25 (see `pom.xml` and `Dockerfile`). The JAR is packaged as `mcp-server.jar` (fixed name
referenced in the Dockerfile).

## Key Configuration (`application.yaml`)

| Setting | Value | Purpose |
|---|---|---|
| `tools.mock: true` | true | Return OpenAPI example responses instead of real API calls |
| `security.auth.enabled` | false | No auth required for mock usage |
| `spring.ai.mcp.server.type` | sync | Synchronous MCP server (async is explicitly unsupported by the framework) |
| `spring.ai.mcp.server.protocol` | STATELESS | Stateless Streamable HTTP transport |
| `open-api-url` | Infobip SMS spec URL | Always fetches the latest spec at startup |

The `api-base-url` is set to a placeholder (`https://example.com`) since mock mode never makes real HTTP calls.

## Connecting a Client

The MCP endpoint is available at `http://localhost:8080/mcp`. Connect using any MCP client that supports Streamable HTTP
transport (e.g., Cursor).

## Framework Reference

For details on how the framework works (OpenAPI loading, tool registration, filter chain, auth), see the parent
project's `CLAUDE.md` at `../../CLAUDE.md`.