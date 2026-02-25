# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A minimal example of `infobip-openapi-mcp-spring-boot-starter` in use: it exposes the [Open-Meteo](https://open-meteo.com) weather API as an MCP server over stdio transport. The entire application logic is configuration — `Application.java` is a standard Spring Boot entry point and `application.yaml` contains all meaningful settings.

## Build

The project uses **Java 25** (set in `pom.xml`). The canonical build path is Docker:

```bash
# Build Docker image (from this directory)
docker build -t open-meteo-mcp .

# Verify the image was created
docker image ls
```

To build the JAR locally without Docker (requires Java 25 on the host):

```bash
mvn clean package
# Output: target/mcp-server.jar
```

## Running & Connecting

```bash
# Register as an MCP server in Claude Code
claude mcp add --transport stdio open-meteo -- docker run --rm -i open-meteo-mcp:latest

# Verify connection
claude mcp list
```

## Debugging

Logs cannot go to stdout (stdio is reserved for the MCP protocol). They are written to `/var/open-meteo-mcp` inside the container, which is exposed as a Docker volume.

```bash
# List volumes to find the one created by the container
docker volume ls

# Read logs (Linux)
cat /var/lib/docker/volumes/<volume-hash>/_data/spring.log

# Read logs (macOS with colima)
colima ssh -- sudo cat /var/lib/docker/volumes/<volume-hash>/_data/spring.log
```

Omit `--rm` from the `docker run` command if you need logs to persist after the container exits (e.g., to debug startup failures).

## Key Configuration (`application.yaml`)

| Setting | Value | Purpose |
|---|---|---|
| `api-base-url` | `https://api.open-meteo.com` | Downstream API host |
| `open-api-url` | Pinned tag on GitHub | Spec source; pinned to avoid unexpected drift |
| `tools.naming.strategy` | `ENDPOINT` | Used because Open-Meteo spec lacks `operationId` |
| `tools.naming.max-length` | `16` | Keeps tool names short |
| `spring.ai.mcp.server.type` | `sync` + `stdio: on` | stdio transport, no web server |
| `logging.threshold.console` | `off` | Prevents log lines from corrupting the MCP stdio stream |
| `logging.file.path` | `/var/open-meteo-mcp` | Log file location (Docker volume) |

## Framework Reference

This example is a consumer of `infobip-openapi-mcp-spring-boot-starter`. The starter's source and documentation live in the parent repository at `infobip-openapi-mcp-core/` and `infobip-openapi-mcp-spring-boot-starter/`. Refer to the root `CLAUDE.md` for framework internals, extension points (`OpenApiFilter`, `ApiRequestEnricher`, `ToolCallFilter`, `NamingStrategy`), and the full architecture.