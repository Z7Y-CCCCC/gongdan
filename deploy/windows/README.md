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

## Operations

```powershell
# Status
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\status.ps1

# Stop
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\stop-backend.ps1

# Optional LAN firewall rule (run as Administrator)
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\setup-firewall.ps1

# Optional backend autostart (run as Administrator)
powershell -NoProfile -ExecutionPolicy Bypass -File C:\www\gongdan\scripts\install-backend-autostart.ps1
```
