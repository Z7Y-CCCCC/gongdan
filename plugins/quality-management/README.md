# Quality Management Plugin Example

This is a standalone plugin used to verify the platform lifecycle. It is intentionally
small and stores demo records in memory; a production plugin should own its database,
migrations, business permissions and observability.

The core service and this plugin must use the same non-empty gateway secret and be
deployed together. The HMAC-SHA256 canonical request joins method, raw path/query,
plugin key, version, factory ID, user ID, role, timestamp, body SHA-256, and the exact
`X-Plugin-Config-Base64` value (empty if absent) with newlines. Configuration is signed
as well as identity and payload; legacy signatures that omit that final line are rejected.
API authentication uses the normalized application path, including requests with matrix
parameters. Demo records are isolated by the authenticated factory ID but remain in memory
and are lost on restart.

## Run locally

```powershell
mvn spring-boot:run
```

The plugin UI is available at `http://127.0.0.1:9101/` and the health endpoint is
`http://127.0.0.1:9101/actuator/health`.

## Register in the core system

Create the plugin, register a READY version, then enable it from the admin UI. The
manifest used for the local host is:

```json
{
  "frontendEntry": "http://127.0.0.1:9101/",
  "backendUrl": "http://127.0.0.1:9101",
  "healthUrl": "/actuator/health",
  "requiredRoles": ["ADMIN", "PRODUCTION"]
}
```

When the core service runs inside Compose, use `http://quality-plugin:9101` for
`backendUrl`; keep `frontendEntry` as the browser-accessible `http://127.0.0.1:9101/`.
