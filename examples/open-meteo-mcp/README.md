# Open-Meteo MCP server

En example project that exposes [Open-Meteo][1] HTTP API as an MCP server using the `infobip-openapi-mcp-spring-boot-starter` framework. Project exposes MCP server over [stdio transport protocol][2].

## Build

Project can be built and packaged locally using Docker.

Run the bash command from the example project's root directory:

```shell
docker build -t open-meteo-mcp .
```

You can verify that build passed by listing available docker images. Output should contain the new `open-meteo-mcp` image, for example:

```shell
$ docker image ls
IMAGE                   ID             DISK USAGE   CONTENT SIZE   EXTRA
open-meteo-mcp:latest   642d196e9572        370MB          107MB        
```

## Usage

You can try it out by connecting to it with an MCP capable AI agent, such as [Claude Code][3], which you can do with a bash command:

```shell
claude mcp add --transport stdio open-meteo -- docker run --rm -i open-meteo-mcp:latest
```

You can verify it with list command, which should how include our new server in its output:

```shell
$ claude mcp list
Checking MCP server health...

open-meteo: docker run --rm -i open-meteo-mcp:latest - ✓ Connected
```

Congratulations, you can now chat about the weather with Claude Code!

![Recording of a terminal Claude Code session in which it reports the current weather for the town of Vodnjan, Croatia](./img/claude-code-open-meteo.gif)

You can usually configure your agent by updating its config file manually. For example, you can connect [Claude Desktop][4] to the same MCP server by updating your `claude_desktop_config.json` file to contain following:

```json
{
  "mcpServers": {
    "open-meteo": {
      "command": "docker",
      "args": [
        "run",
        "--rm",
        "-i",
        "open-meteo-mcp:latest"
      ]
    }
  }
}
```

### MCP Apps

The example also demonstrates [MCP Apps][5] — tools declaring an interactive HTML UI. Two Spring beans in
`com.infobip.mcp.example` add this on top of the Open-Meteo specification at startup, since that remote specification
cannot itself carry MCP Apps annotations:

- `McpAppsSpecEnhancer` (an `OpenApiFilter`) injects the vendor extensions into the fetched spec: it marks a new
  `/dashboard` operation with `x-mcp-app-resource` (exposing a `ui://open-meteo-dashboard` UI resource) and links the
  forecast operation to that UI with `x-mcp-app`. Linked tools additionally return `structuredContent` alongside the
  usual text content on successful calls.
- `DashboardResourceFilter` (a `ResourceCallFilter`) short-circuits reads of that UI resource and serves the bundled
  `dashboard.html` directly, instead of calling the (nonexistent) Open-Meteo dashboard endpoint. This shows how to
  serve a UI document from the application itself rather than from the downstream API.

The `dashboard.html` is a static placeholder. A production widget would load the MCP Apps bridge and exchange
messages with the host to render live forecast data here.

> [!NOTE]
> This example builds against the in-development `1.0.3-SNAPSHOT` of the framework. Before building the image, install
> the framework into your local Maven repository from the repository root:
>
> ```shell
> mvn install -DskipTests
> ```
>
> The Docker build runs `mvn dependency:go-offline` in a clean container, so the snapshot dependency must already be
> published to a repository that build can reach. To try the image before `1.0.3` is released, either publish the
> snapshot or build and run the example directly with `mvn spring-boot:run` against the locally installed framework.

### Debug

Debugging is a little bit tricky, because MCP servers running on localhost use stdio for protocol transport. This means
that we can't write debug and other logs to stdout and have them available via docker logs like we usually do. To work around
this the container writes logs into a file in a docker volume. You can list docker volumes with `docker volume ls`, and check logs with something like:

```shell
cat /var/lib/docker/volumes/9d4...hash_of_the_volume_on_your_machine...f21/_data/spring.log
```

If running docker with colima under macOS access logs inside colima:

```shell
colima ssh -- sudo cat /var/lib/docker/volumes/9d4...hash_of_the_volume_on_your_machine...f21/_data/spring.log
```

If you need logs to persist after MCP server exists (for example to troubleshoot startup issues) omit the `--rm` argument from the docker command.

---

[1]: https://open-meteo.com

[2]: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#stdio "stdio transport in MCP documentation"

[3]: https://claude.com/product/claude-code

[4]: https://claude.com/product/overview

[5]: https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx "MCP Apps (SEP-1865) specification"