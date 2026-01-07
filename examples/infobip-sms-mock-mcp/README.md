# Infobip SMS Mock MCP Server

En example project that exposes a mock of Infobip's official SMS MCP server using the `infobip-openapi-mcp-spring-boot-starter` framework. Project exposes MCP server over [Streamable HTTP transport protocol][1]. The mock MCP server can be used in testing MCP clients to see how they behave without connecting to production HTTP APIs.

## Build

Project can be built and packaged locally using Docker.

Run the bash command from the example project's root directory:

```shell
docker build -t infobip-sms-mock-mcp .
```

You can verify that build passed by listing available docker images. Output should contain the new `infobip-sms-mock-mcp` image, for example:

```shell
$ docker image ls
IMAGE                           ID             DISK USAGE   CONTENT SIZE   EXTRA
infobip-sms-mock-mcp:latest   642d196e9572        370MB          107MB
```

## Run

Run the mock MCP server in interactive mode on `localhost` on port `8080` using Docker:

```shell
docker run --rm -ti -p 8080:8080 infobip-sms-mock-mcp:latest
```

You should see the standard Spring application startup logs in the terminal. Successful run will finish with logs resembling these:

```
2026-01-07T10:33:19.774Z  INFO 1 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8080 (http)
2026-01-07T10:33:19.780Z  INFO 1 --- [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-01-07T10:33:19.781Z  INFO 1 --- [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.48]
2026-01-07T10:33:19.792Z  INFO 1 --- [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2026-01-07T10:33:19.792Z  INFO 1 --- [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 439 ms
2026-01-07T10:33:20.004Z  INFO 1 --- [           main] c.i.openapi.mcp.openapi.OpenApiRegistry  : Loading OpenAPI from https://api.infobip.com/platform/1/openapi/mcp/sms.
2026-01-07T10:33:20.586Z  INFO 1 --- [           main] c.i.o.m.o.filter.OpenApiFilterChain      : Applying OpenAPI filter: DiscriminatorFlattener.
2026-01-07T10:33:20.587Z  INFO 1 --- [           main] c.i.o.m.o.filter.OpenApiFilterChain      : Applying OpenAPI filter: PatternPropertyRemover.
2026-01-07T10:33:20.733Z  INFO 1 --- [           main] c.i.openapi.mcp.openapi.OpenApiRegistry  : Successfully loaded OpenAPI from https://api.infobip.com/platform/1/openapi/mcp/sms.
2026-01-07T10:33:20.759Z  WARN 1 --- [           main] s.m.p.r.SyncStatelessMcpResourceProvider : No resource methods found in the provided resource objects: []
2026-01-07T10:33:20.759Z  WARN 1 --- [           main] s.m.p.r.SyncStatelessMcpResourceProvider : No resource methods found in the provided resource objects: []
2026-01-07T10:33:20.760Z  WARN 1 --- [           main] o.s.m.p.p.SyncStatelessMcpPromptProvider : No prompt methods found in the provided prompt objects: []
2026-01-07T10:33:20.761Z  WARN 1 --- [           main] s.m.p.c.SyncStatelessMcpCompleteProvider : No complete methods found in the provided complete objects: []
2026-01-07T10:33:20.762Z  WARN 1 --- [           main] o.s.m.p.t.SyncStatelessMcpToolProvider   : No tool methods found in the provided tool objects: []
2026-01-07T10:33:20.766Z  INFO 1 --- [           main] .c.a.McpServerStatelessAutoConfiguration : Registered tools: 8
2026-01-07T10:33:20.766Z  INFO 1 --- [           main] .c.a.McpServerStatelessAutoConfiguration : Enable completions capabilities
2026-01-07T10:33:20.819Z  INFO 1 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
2026-01-07T10:33:20.825Z  INFO 1 --- [           main] com.infobip.mcp.example.Application      : Started Application in 1.667 seconds (process running for 1.832)
```

Note the `Tomcat started on port 8080` and `Registered tools: 8` parts. This means MCP server is accessible on port `8080` and that it loaded 8 tools from the OpenAPI specification that we provided.

> [!NOTE]
> This example is using the latest version of Infobip's SMS OpenAPI specification. With time new API endpoints might get introduced, and existing endpoints might get deprecated and removed. Over time the number of tools exposed by this mock MCP server will change accordingly.
> 
> In production, you may want to lock the version of OpenAPI specification, especially when targeting a 3rd party API. See the [open-meteo-mcp](../open-meteo-mcp) example to learn how. With the mock MCP server, especially if it is used in testing, you may want to always pull the latest spec version, so that your tests always run against the currently deployed API specification. Without breaking changes on the API side it should not matter, but this approach will let you catch any issues in your tests.

## Use

You can try it out by connecting to it with an MCP capable AI agent that supports Streamable HTTP transport, such as [Cursor editor][2]. With Cursor app installed on your machine click this link to install your mock MCP server running on localhost: [https://cursor.com/en-US/install-mcp?name=infobip-sms-mock&config=eyJ1cmwiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvbWNwIn0%3D](https://cursor.com/en-US/install-mcp?name=infobip-sms-mock&config=eyJ1cmwiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvbWNwIn0%3D)

Congratulations, you can now ask agent inside Cursor to send SMS text messages to the mock server!

![Recording of installing mock MCP server in Cursor and interacting with it through app's AI agent](./img/cursor-infobip-sms-mock.gif)

---

[1]: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http "Streamable HTTP transport in MCP documentation"

[2]: https://cursor.com/home