# Quality Management Plugin Example

This is a standalone plugin used to verify the platform lifecycle. It is intentionally
small, but successful record saves now use the plugin's own file-backed H2 database
and survive process restarts. Customer-specific workflows, migrations and production
backup/restore still need separate acceptance.

The core service and this plugin must use the same non-empty gateway secret and be
deployed together. The HMAC-SHA256 canonical request joins method, raw path/query,
plugin key, version, factory ID, user ID, role, timestamp, body SHA-256, and the exact
`X-Plugin-Config-Base64` value (empty if absent) with newlines. Configuration is signed
as well as identity and payload; legacy signatures that omit that final line are rejected.
API authentication uses the normalized application path, including requests with matrix
parameters. Record queries are isolated by the authenticated factory ID in SQL.

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

## Durable records

The default database is `./data/quality-management.mv.db`, relative to the process
working directory. Set `PLUGIN_DATA_DIR` to an absolute writable directory for a
service installation. Compose mounts the independent `quality-plugin-data` volume;
rebuilding or restarting the container does not delete it. `docker compose down -v`
does delete named volumes and must not be used to retain records.

`PLUGIN_DATABASE_PASSWORD` (Compose: `QUALITY_PLUGIN_DATABASE_PASSWORD`) can protect the database with credentials; keep it stable
after the first startup. It is not a replacement for filesystem access controls or
encryption at rest. A write failure returns an error, not a successful saved record.
IDs are database-generated, so restart and simultaneous saves do not reuse IDs.
Issue/remark lengths are limited to 500 characters and status to 32; invalid inputs
return HTTP 400 before a write.

Older versions kept records only in memory. Export any live demo data before upgrading;
already-lost memory records cannot be recovered by this change. Stop the plugin before
copying its database for a consistent offline backup. Multi-instance writes, live
backup and production recovery procedures are not validated by these local tests.

Run `mvn -B clean package` for signed API, disk restart and factory-isolation regressions.
From the repository root, `node tools/functional/check-plugins.mjs` also tests freshly
packaged JARs over isolated loopback HTTP, including a real process restart.
