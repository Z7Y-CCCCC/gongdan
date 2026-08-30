# Windows Internal Demo Deployment

This package runs beside the existing form system without Docker:

- backend: `127.0.0.1:18080`
- nginx entry: `0.0.0.0:8088`
- admin UI: `/`
- mobile H5: `/mobile/`
- API: `/api/`

## Install

1. Copy the generated release folder to `C:\www\gongdan` on the Windows Server.
2. Edit `C:\www\gongdan\config\gongdan.env` and replace all `CHANGE_ME` values. Confirm the MySQL 9.2 port; common values are `3306` and `3308`.
3. Add `nginx\gongdan-server.conf` inside the existing nginx `http { }` block, then run `nginx.exe -t` and `nginx.exe -s reload` from the nginx directory.
4. Start the backend:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\start-backend.ps1
```

5. Open `http://SERVER_IP:8088/` and `http://SERVER_IP:8088/mobile/` from the office LAN.
6. When LAN testing passes, place `cloudflared.exe` under `C:\www\gongdan\tools` and run `scripts\start-quick-tunnel.ps1`. Share the generated HTTPS URL only with the internal testers.

The demo profile uses mock WeChat identity, log-only SMS, and Hibernate schema updates because the Java 8 compatible Flyway line does not support MySQL 9.2. It is for workflow verification, not public production. Uploaded images are stored under `C:\www\gongdan\data\uploads`.

For a commercial deployment, copy `config\gongdan.prod.env.example` to `config\gongdan.env`, use MySQL 8.4 LTS with this Java 8/Spring Boot/Flyway stack, configure a real HTTPS origin, WeChat credentials, SMS gateway, non-root application/backup/restore database accounts, and an independent S3-compatible backup bucket with Versioning and Object Lock. Install AWS CLI v2, then run the preflight before starting:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\verify-release.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\preflight.ps1 -Mode Commercial
```

## Reliability Setup

Before installing the maintenance tasks, set these values in `config\gongdan.env`:

- `MYSQL_SERVICE_NAME`: the Windows service name, normally `MySQL92`.
- `BACKUP_MYSQL_EXE`: optional absolute path to MySQL 9.2 `mysqldump.exe`; the script auto-detects the standard install path.
- `BACKUP_MYSQL_USER` / `BACKUP_MYSQL_PASSWORD`: dedicated local-only backup identity with dump, log rotation, and replication-client permissions.
- `RESTORE_MYSQL_USER` / `RESTORE_MYSQL_PASSWORD`: dedicated local-only recovery identity; protect and review it as a privileged account.
- `BACKUP_DIR`: local fast-restore directory. `BACKUP_SECONDARY_DIR` is optional but useful for a second disk or NAS.
- `BACKUP_REMOTE_*`: independent immutable object storage identity, endpoint, region, encryption, and bucket URI.
- `BINLOG_ARCHIVE_DIR`: closed binary log archive, uploaded every five minutes.
- `BACKUP_RETENTION_DAYS`: database and attachment backup retention, default 35 days.
- `LOG_RETENTION_DAYS`: host log retention, default 30 days.
- `NGINX_SERVICE_NAME`: use this when nginx is installed as a Windows service.
- `NGINX_EXE` and `NGINX_CONFIG_DIR`: use these when nginx is a regular executable instead of a service.

Run once as Administrator:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\install-maintenance-tasks.ps1
```

This installs SYSTEM tasks for:

- startup recovery: waits for MySQL, starts the backend, starts nginx when configured, and requires the `/health` endpoint to pass;
- database backup: runs every day at 02:00 and writes SQL, SHA-256, attachment archive, and manifest files;
- binlog archive: runs every five minutes, copies only closed logs, verifies size/hash, and uploads them to immutable storage;
- restore drills: run weekly against temporary databases for both full restore and PITR replay;
- log cleanup: runs every day at 04:00 and deletes only matching expired log/backup files.
- health monitor: runs every five minutes and checks web, mobile, backend, MySQL, full backup status, binlog freshness/checksum, disk space, and the optional alert webhook.

MySQL InnoDB performs crash recovery when the MySQL service starts after a power loss. The startup task then restores the application service chain. It does not silently import an old backup over a healthy database.

For unattended recovery after a site power outage, also enable the server BIOS/UEFI setting usually named `Restore on AC Power Loss`, `AC Back`, or `After Power Failure` and choose `Power On` or `Last State`. Windows scheduled tasks can recover the software stack after boot, but they cannot power on a machine that remains off. A UPS is still recommended to reduce abrupt database shutdowns.

## Logs and Retention

- backend runtime log: `C:\www\gongdan\logs\backend.log` with size/date rolling;
- backend launcher output: timestamped `backend_*.out.log` and `backend_*.err.log`;
- startup recovery log: `startup-recovery.log`;
- backup and cleanup result log: `maintenance.log`;
- database operation/import logs: retained for `APP_SYSTEM_LOG_RETENTION_DAYS` (default 365) and cleaned by the backend at 03:30.

API write requests record request ID, method, path, status, duration, and client address. Passwords, authorization headers, request bodies, and query strings are not logged.

## Operations

```powershell
# Status
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\status.ps1

# Stop
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\stop-backend.ps1

# Run a database and attachment backup now
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\backup-database.ps1

# Non-destructive restore drill against a temporary database
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\test-restore.ps1

# Non-destructive point-in-time replay drill against a temporary database
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\test-pitr-restore.ps1

# Rotate/archive closed binary logs now
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\archive-binlogs.ps1

# Commercial smoke test; add -Login during a maintenance window
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\smoke-test.ps1 -Login

# Destructive restore; the database name must match the configured JDBC database
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\restore-backup.ps1 -ConfirmDatabase heihu_workorder

# Preview expired log and backup cleanup
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\cleanup-logs.ps1 -WhatIf

# Run cleanup now
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\cleanup-logs.ps1

# Optional LAN firewall rule (run as Administrator)
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\setup-firewall.ps1

# Startup recovery only (the full maintenance installer above is preferred)
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\install-backend-autostart.ps1
```

After setup, run `status.ps1`. It reports backend health, MySQL service state, latest database backup, and the last startup recovery result. Test the recovery path once by restarting the server during a maintenance window and confirming both admin and mobile pages load without manually starting Java.
