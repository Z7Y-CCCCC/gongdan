# Work-order MCP diagnostics gateway

This directory contains a read-only Model Context Protocol (MCP) server for the
work-order system. It uses the official MCP TypeScript SDK and Streamable HTTP.
The gateway calls the existing Spring Boot HTTP API; it does not connect to
MySQL or Redis and it cannot execute arbitrary SQL, shell commands, or write
business data.

## What it provides

The server exposes these tools:

- `system_health`: backend liveness and readiness.
- `list_diagnostic_endpoints`: the exact read-only API allowlist.
- `inspect_api`: one allowlisted GET endpoint with status, latency, and sanitized body.
- `run_smoke_test`: a bounded sequential smoke test for important GET endpoints.
- `search_system_logs`: paginated import or operation audit logs.
- `inspect_online_sessions`: active sessions and login metadata.
- `inspect_cache`: cache metrics, cache names, and recovery records. Cache keys
  and values are intentionally not exposed.

Every tool is annotated as read-only and non-destructive. The gateway rejects
non-allowlisted paths and unknown query parameters before making a backend
request.

Configured Host/Origin allowlists are enforced by the transport. Backend
redirects are reported without being followed; response bodies are capped while
streaming, and non-JSON bodies are omitted because they cannot be field-redacted.
Concurrent expired-session responses share a single backend sign-in refresh.

## Local run

Use Node.js 20 or newer:

```powershell
cd ops\mcp
npm ci
$env:MCP_API_KEY = "local-only-change-me"
$env:MCP_BACKEND_URL = "http://127.0.0.1:8080"
$env:MCP_BACKEND_FACTORY_CODE = "HHGD"
$env:MCP_BACKEND_USERNAME = "mcp-diagnostics"
$env:MCP_BACKEND_PASSWORD = "<dedicated-admin-password>"
npm start
```

The endpoint is `http://127.0.0.1:9300/mcp`; liveness is
`http://127.0.0.1:9300/healthz`.

Run the protocol test without a running backend:

```powershell
npm test
```

## Production configuration

Copy `.env.example` into the deployment secret store. In production,
`MCP_API_KEY` is mandatory and the process defaults to binding on loopback.
Put a TLS reverse proxy in front of the service if an Agent connects from
another host, restrict the firewall to trusted Agent networks, and configure
`MCP_ALLOWED_HOSTS` for the public host name.

Create a dedicated enabled backend admin account for MCP diagnostics. The
backend currently invalidates older sessions when the same user logs in again,
so sharing the normal human admin account would unexpectedly sign that person
out. `MCP_BACKEND_TOKEN` can be used instead of credentials when a separately
managed, expiring Bearer token is preferred.

Do not put the backend password or token in source control, browser code,
agent prompts, tool output, or logs. The gateway redacts keys containing
password, token, secret, authorization, cookie, access key, phone, or mobile.

## Docker Compose

The production Compose file includes an optional `mcp` profile. It is not
started by the default stack. Start it only after supplying the MCP variables
in `.env`:

```bash
docker compose --profile mcp up --build -d mcp
```

Keep port `9300` private or publish it only through an authenticated TLS
reverse proxy. The Compose healthcheck uses `/healthz` and does not expose
business data.

## Agent connection

Configure an MCP client with the Streamable HTTP URL and an HTTP Bearer token:

```json
{
  "mcpServers": {
    "heihu-workorder": {
      "url": "https://mcp.example.com/mcp",
      "headers": {
        "Authorization": "Bearer ${MCP_API_KEY}"
      }
    }
  }
}
```

The Agent can first call `list_diagnostic_endpoints`, then use
`inspect_api` for a single issue or `run_smoke_test` after a release. The
gateway is intentionally diagnostic-only in this first version; any write or
repair workflow should be introduced as a separately reviewed tool with its
own authorization and audit policy.
