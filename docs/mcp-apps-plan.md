# MCP Apps support — implementation plan

Status: **Implemented** (all of §5–§8 delivered on the current working tree)

## 1. Goal

Let framework users expose interactive HTML UIs ("MCP Apps") alongside their OpenAPI-derived tools, following the
MCP Apps extension (SEP-1865) as closely as the current Java SDK allows. A user should be able to:

1. Mark a backend `GET` operation that returns HTML as an **MCP App resource** (a `ui://` resource).
2. Link any tool to such a resource, so that MCP Apps–capable hosts (Claude, ChatGPT, VS Code Copilot, Goose, …)
   render the UI when the tool is called, while all other hosts keep working with the existing text result.

## 2. Protocol reference

| Item                        | Value                                                                                                  |
|-----------------------------|--------------------------------------------------------------------------------------------------------|
| Extension identifier        | `io.modelcontextprotocol/ui`                                                                           |
| Target spec                 | MCP Apps stable `2026-01-26`, plus draft rule that `_meta.ui` may appear on list entry and content item |
| UI resource URI scheme      | `ui://` (reserved by the spec)                                                                         |
| UI resource MIME type       | `text/html;profile=mcp-app` (MUST be exact on `resources/read` content)                                |
| Tool linkage                | `Tool._meta.ui.resourceUri`, `Tool._meta.ui.visibility` (`["model","app"]` default)                    |
| Deprecated tool linkage     | `Tool._meta["ui/resourceUri"]` (still emitted by the official TS helper for back-compat)                |

Sources:

- Spec (stable): https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx
- Spec (draft): https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/draft/apps.mdx
- Docs: https://modelcontextprotocol.io/extensions/apps/overview, https://modelcontextprotocol.io/extensions/client-matrix
- Java SDK extension tracking issue: https://github.com/modelcontextprotocol/java-sdk/issues/999

### 2.1 UI resource `_meta.ui` fields

| Key                   | Type       | Meaning                                                          |
|-----------------------|------------|------------------------------------------------------------------|
| `csp.connectDomains`  | `string[]` | Origins allowed for `connect-src` (fetch/XHR/WebSocket)           |
| `csp.resourceDomains` | `string[]` | Origins allowed for scripts, styles, images, fonts, media        |
| `csp.frameDomains`    | `string[]` | Origins allowed for nested iframes (`frame-src`)                 |
| `csp.baseUriDomains`  | `string[]` | Origins allowed for `base-uri`                                   |
| `permissions`         | object     | `camera`, `microphone`, `geolocation`, `clipboardWrite` (each `{}`) |
| `domain`              | string     | Dedicated sandbox origin; format is host-specific                |
| `prefersBorder`       | boolean    | Whether the host should draw a border around the view            |

When `csp` is omitted, hosts apply a restrictive default (no external origins). Hosts MUST NOT allow undeclared
origins, so accurate CSP declaration is the server's only security obligation.

### 2.2 Host responsibilities (no server action needed)

- Hosts hide tools whose `visibility` lacks `"model"` from the model, and reject app-initiated calls to tools whose
  `visibility` lacks `"app"`. The server still lists **all** tools in `tools/list`.
- App-initiated `tools/call` and `resources/read` are proxied by the host and reach the server as ordinary requests.
- The host forwards the full `CallToolResult` (including `structuredContent` and `_meta`) to the app via
  `ui/notifications/tool-result`.

## 3. Decisions

| # | Topic                     | Decision                                                                                          |
|---|---------------------------|---------------------------------------------------------------------------------------------------|
| 1 | Source of UI HTML         | A backend `GET` operation, reusing the existing resource pipeline                                  |
| 2 | Client capability gating  | Always emit. Java SDK 2.0.x drops `capabilities.extensions`; hosts without MCP Apps ignore `_meta` |
| 3 | `structuredContent`       | Only for tools linked to an app, on successful JSON responses                                      |
| 4 | Non-object JSON           | Wrap into `{"result": <value>}`; decision based on the declared response schema                   |
| 5 | Compatibility keys        | Emit `_meta.ui.*` and the deprecated flat `_meta["ui/resourceUri"]`; no `openai/*` aliases         |
| 6 | YAML override for linkage | Not included; spec-only for now (can be added later without breaking changes)                      |
| 7 | Vendor extension names    | `x-mcp-app-resource` (UI resource operation) and `x-mcp-app` (tool linkage)                        |
| 8 | Server capability         | Not declared; the SDK has no `extensions` field. Revisit when java-sdk#999 lands                   |

## 4. User-facing API

### 4.1 `x-mcp-app-resource` — declare a UI resource

Placed on a `GET` operation whose response is an HTML document.

```yaml
paths:
  /apps/sms-dashboard:
    get:
      summary: SMS delivery dashboard
      x-mcp-app-resource:
        uri: ui://sms/dashboard            # optional, defaults to ui://<path>, e.g. ui://apps/sms-dashboard
        csp:                               # optional
          connectDomains: [https://api.example.com]
          resourceDomains: [https://cdn.example.com]
          frameDomains: []
          baseUriDomains: []
        permissions:                       # optional
          clipboardWrite: {}
        domain: abc123.claudemcpcontent.com # optional
        prefersBorder: true                # optional
      responses:
        '200':
          content:
            text/html:
              schema: { type: string }
```

A value of `true` is also accepted as shorthand for "UI resource with defaults".

Behaviour:

- The operation becomes a **static** MCP resource (never a template) and is excluded from the tool list, like
  `x-mcp-resource`.
- Listed in `resources/list` with URI from `uri` (or the default), MIME type `text/html;profile=mcp-app`, name from the
  configured `NamingStrategy`, title/description from summary/description, and `_meta.ui` from the extension.
- `resources/read` calls the backend, then forces the content MIME type to exactly `text/html;profile=mcp-app` and
  merges `_meta.ui` into each content item. Both text and base64 blob content are valid.

### 4.2 `x-mcp-app` — link a tool to a UI resource

Placed on any operation that becomes a tool.

```yaml
paths:
  /sms/reports:
    get:
      x-mcp-app:
        resourceUri: ui://sms/dashboard    # required
        visibility: [model, app]           # optional, omitted from _meta when not set
```

Resulting tool definition:

```json
{
  "name": "get-sms-reports",
  "_meta": {
    "ui": { "resourceUri": "ui://sms/dashboard", "visibility": ["model", "app"] },
    "ui/resourceUri": "ui://sms/dashboard"
  }
}
```

### 4.3 Tool results of app-linked tools

For successful (2xx) JSON responses, the existing text `content` is kept unchanged (required as a fallback for the
model and for hosts without MCP Apps), and `structuredContent` is added:

| Declared response schema (best 2xx, JSON media type)            | `structuredContent`            |
|-----------------------------------------------------------------|--------------------------------|
| Object (`type: object`, `properties`, `allOf`/`oneOf`/`anyOf` of objects, `$ref` to object) | The parsed body, as-is |
| Array or scalar                                                 | `{"result": <parsed body>}`    |
| Missing / undeterminable                                         | Decided by the runtime value: object as-is, anything else wrapped |

The shape is decided from the declared schema so it is stable per tool and predictable for app authors. If the body
is not valid JSON, or the response is an error, `structuredContent` is omitted. Tools without `x-mcp-app` are not
affected.

### 4.4 Startup validation

| Condition                                                        | Result                             |
|------------------------------------------------------------------|------------------------------------|
| `x-mcp-app-resource` on a non-`GET` operation                    | Startup error                      |
| `x-mcp-app-resource` operation with path or query parameters     | Startup error (templates not supported by the spec) |
| `x-mcp-app-resource.uri` or `x-mcp-app.resourceUri` not starting with `ui://` | Startup error         |
| `visibility` contains values other than `model` / `app`, or is empty | Startup error                  |
| Duplicate `ui://` URIs across operations                          | Startup error                      |
| `x-mcp-app` and `x-mcp-app-resource` on the same operation        | Startup error                      |
| `x-mcp-app.resourceUri` does not match any declared app resource  | Warning (may be registered by application code) |

Errors use the existing `ResourceRegistrationException` style and fail fast; with live reload they follow the
existing reload failure handling (previous state is kept).

## 5. Implementation

### 5.1 Core (`infobip-openapi-mcp-core`)

**New package `com.infobip.openapi.mcp.apps`** (with `package-info.java` annotated `@NullMarked`):

- `AppResourceExtension` — record parsed from `x-mcp-app-resource` (`uri`, `csp`, `permissions`, `domain`,
  `prefersBorder`), validated in its compact constructor. Parsed with the injected Jackson 3 `ObjectMapper` via
  `convertValue`, same approach as `PromptRegistry` uses for `x-mcp-prompts`. Nested records for `Csp`.
- `AppToolExtension` — record parsed from `x-mcp-app` (`resourceUri`, `visibility`), validated in its compact
  constructor; `visibility` is an enum (`MODEL`, `APP`) serialised lower-case.
- `AppMeta` — constants (`EXTENSION_ID`, `MIME_TYPE`, `URI_SCHEME`, meta keys) and methods converting the records
  into `_meta` maps (`LinkedHashMap`, unset fields omitted).

**`openapi/schema/Spec`**

- Add `MCP_APP_RESOURCE_EXTENSION = "x-mcp-app-resource"` and `MCP_APP_EXTENSION = "x-mcp-app"`.
- Add `isAppResourceOperation(Operation)`; make `isResourceOperation` return true for either `x-mcp-resource` or
  `x-mcp-app-resource`, so `ToolRegistry` exclusion keeps working unchanged.

**`resource/ResourceRegistry`**

- In `toRegisteredResource`, branch for app resource operations: validate (GET, no parameters), compute URI (explicit
  or `ui://` + path), build `McpSchema.Resource` with `mimeType(MIME_TYPE)` and `meta(Map.of("ui", ...))`.
- Validate uniqueness of `ui://` URIs.
- Expose the set of registered app resource URIs for the tool cross-check.

**`resource/ResourceHandler`**

- After `ResourceContentsConverter.convert(...)`, for app resources rewrite each `TextResourceContents` /
  `BlobResourceContents` with MIME type `text/html;profile=mcp-app` and merged `_meta.ui` (existing content meta is
  preserved, `ui` key from the extension wins). Done in the handler, not the converter, so custom converters keep
  working without changes.
- The handler needs to know the app metadata: pass it from `ResourceRegistry` via the handler lambda (e.g. an
  overload `handleResourceRead(..., @Nullable AppResourceExtension)`), keeping the existing public signature intact.

**`openapi/tool/ToolRegistry`**

- Parse `x-mcp-app` and set `Tool.builder().meta(...)` with `ui` and `ui/resourceUri`.
- After building tools, log a warning for `resourceUri` values not present among app resources.
- Keep the parsed `AppToolExtension` available to the handler (via `RegisteredTool` construction / closure).

**`openapi/tool/ToolHandler`**

- For app-linked tools with a successful JSON response, build `structuredContent` according to §4.3 and add it to the
  `CallToolResult` alongside the existing text content. `callToolResult(text, isError)` gets a sibling that accepts
  structured content.
- The declared-schema check (object vs non-object) is computed once at registration time, not per call.
- Apply the same to the mock path (`ToolResultMocker`) so mocked app tools behave identically.
- `JsonDoubleSerializationCorrector` retry path must keep `structuredContent` intact.

**`openapi/ToolLiveReload`**

- No code change expected: tool and resource diffs use record `equals`, which includes `_meta`. Add tests to confirm
  that changing only `x-mcp-app` / `x-mcp-app-resource` metadata is detected as an update.

### 5.2 Starter (`infobip-openapi-mcp-spring-boot-starter`)

- No new beans or properties. Existing resource/tool registration covers SSE, Streamable HTTP, Stateless and stdio.
- If the `ResourceHandler` or `ToolRegistry` constructors change, update the wiring in `OpenApiMcpConfiguration`
  while keeping existing public constructors for backwards compatibility.

### 5.3 Backwards compatibility

- No behaviour change for specs without the new extensions.
- `ResourceContentsConverter`, `ResourceCallFilter`, `ToolCallFilter`, `NamingStrategy` implementations in
  application code keep working unchanged.
- Public constructors / method signatures are preserved; new parameters are added through overloads.

## 6. Tests

### 6.1 Unit tests (core)

- `ResourceRegistryTest`: app resource listed as static resource; default and explicit `ui://` URI; MIME type;
  `_meta.ui` contents (full and minimal); `true` shorthand; every validation error from §4.4.
- `ResourceHandlerTest`: MIME type forced and `_meta.ui` merged for text and blob content; custom converter output is
  post-processed; non-app resources unaffected.
- `ToolRegistryTest`: `_meta.ui` and `ui/resourceUri` emitted; visibility omitted when unset; validation errors;
  warning for unknown `resourceUri`; tools without `x-mcp-app` have no `_meta`.
- `ToolHandlerTest` (parameterized): object schema → body as-is; array/scalar schema → wrapped; missing schema →
  runtime decision; non-JSON body and error responses → no `structuredContent`; non-app tools unchanged.
- `ToolResultMockerTest`: mocked app tool returns `structuredContent`.
- `ToolLiveReloadTest`: metadata-only change triggers tool/resource update notifications.
- New `AppMetaTest` / extension record tests for parsing and validation.

### 6.2 Integration tests (starter)

- `McpAppsIntegrationTest` with `@Nested` Streamable, SSE and Stateless variants extending a new `McpAppsTestBase`,
  following `ResourceIntegrationTest` / `ResourceTestBase`. WireMock serves the OpenAPI spec, the HTML page and a JSON
  API. Assertions:
  - `tools/list` returns `_meta.ui` and `ui/resourceUri`;
  - `resources/list` returns the `ui://` resource with the exact MIME type and `_meta.ui`;
  - `resources/read` returns the HTML with the exact MIME type and `_meta.ui`;
  - `tools/call` returns text `content` plus correct `structuredContent` (object and wrapped array cases).

### 6.3 Example

- Add a small HTML dashboard to `examples/open-meteo-mcp` (served from the example app itself) linked to the forecast
  tool, to try manually in an MCP Apps–capable host (Claude Desktop, MCPJam).

## 7. Documentation

- `README.md`: new "MCP Apps" section — both extensions with examples, CSP guidance, `structuredContent` shape and
  wrapping rule, text fallback, and the note that client-capability gating is not possible yet. Reference-style
  links only.
- `CHANGELOG.md`: `### Added` entry under `[Unreleased]` describing MCP Apps support from a user perspective.
- `CLAUDE.md`: add the app resource / app tool metadata step to the architecture section.
- Run `mvn spotless:apply` as the final step.

## 8. Implementation order

1. `apps` package: extension records, `AppMeta`, unit tests.
2. `Spec` constants and `isAppResourceOperation`.
3. `ResourceRegistry` + `ResourceHandler` changes with tests.
4. `ToolRegistry` `_meta` + cross-check with tests.
5. `ToolHandler` / `ToolResultMocker` `structuredContent` with tests.
6. `ToolLiveReload` tests.
7. Starter wiring adjustments and integration tests.
8. Example app, README, CHANGELOG, CLAUDE.md, `mvn spotless:apply`, `mvn verify`.

## 9. Out of scope / future work

- **Client capability gating** on `capabilities.extensions["io.modelcontextprotocol/ui"]` and declaring the server
  extension capability — blocked on MCP Java SDK support (java-sdk#999).
- **YAML override** for tool linkage (`infobip.openapi.mcp.tools.app.<tool-name>.*`).
- **Other UI sources** (inline HTML in the spec, classpath/config-based resources).
- **`outputSchema` generation** from the OpenAPI response schema (the wrapping rule in §4.3 is compatible with it).
- **OpenAI Apps SDK aliases** (`openai/outputTemplate`, `openai/widgetCSP`, …).
- **Core spec `2026-07-28`** stateless features (`ttlMs`, `cacheScope`, per-request capabilities) — depends on SDK.

## 10. Open points

- Wrapper key name for non-object results: proposed `result`. Fixed constant, documented in README.
- Default URI for app resources: proposed `ui://<path without leading slash>`; alternatively derive from the tool
  naming strategy (`ui://<name>`).