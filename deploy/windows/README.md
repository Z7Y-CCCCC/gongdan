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

## Reliability Setup

Before installing the maintenance tasks, set these values in `config\gongdan.env`:

- `MYSQL_SERVICE_NAME`: the Windows service name, normally `MySQL92`.
- `BACKUP_MYSQL_EXE`: optional absolute path to MySQL 9.2 `mysqldump.exe`; the script auto-detects the standard install path.
- `BACKUP_DIR`: local backup directory. Keep a second copy on another disk or NAS with `BACKUP_SECONDARY_DIR`.
- `BACKUP_RETENTION_DAYS`: database and attachment backup retention, default 30 days.
- `LOG_RETENTION_DAYS`: host log retention, default 30 days.
- `NGINX_SERVICE_NAME`: use this when nginx is installed as a Windows service.
- `NGINX_EXE` and `NGINX_CONFIG_DIR`: use these when nginx is a regular executable instead of a service.

Run once as Administrator:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\install-maintenance-tasks.ps1
```

This installs three SYSTEM tasks:

- startup recovery: waits for MySQL, starts the backend, starts nginx when configured, and requires the `/health` endpoint to pass;
- database backup: runs every day at 02:00 and writes SQL, SHA-256, attachment archive, and manifest files;
- log cleanup: runs every day at 04:00 and deletes only matching expired log/backup files.

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
