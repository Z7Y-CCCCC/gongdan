# Quality Management Plugin Example

This is a standalone plugin used to verify the platform lifecycle. It is intentionally
small and stores demo records in memory; a production plugin should own its database,
migrations, business permissions and observability.

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
