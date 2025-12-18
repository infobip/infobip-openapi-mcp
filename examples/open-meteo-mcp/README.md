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

### Debug

Debugging is a little bit tricky, because MCP servers running on localhost use stdio for protocol transport. This means
that we can't write debug and other logs to stdout and have them available via docker logs like we usually do. To work around
this the container writes logs into a file in a docker volume. You can list docker volumes with `docker volume ls`, and check logs with something like:

```shell
cat /var/lib/docker/volumes/9d4...your.hash.will.differ.f21/_data/spring.log
```

If running docker with colima under macOS access logs inside colima:

```shell
colima ssh -- sudo cat /var/lib/docker/volumes/9d4...your.hash.will.differ.f21/_data/spring.log
```

If you need logs to persist after MCP server exists (for example to troubleshoot startup issues) omit the `--rm` argument from the docker command.

---

[1]: https://open-meteo.com

[2]: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#stdio "stdio transport in MCP documentation"

[3]: https://claude.com/product/claude-code

[4]: https://claude.com/product/overview