# 商业运维手册

## 日常检查

```powershell
cd C:\www\gongdan
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\status.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\monitor.ps1
```

检查内容：后端健康、MySQL 服务、最新备份时间和校验、磁盘空间、网页入口、移动入口、维护日志和 `runtime\monitor-status.json`。

## 启停与恢复

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-stack.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-backend.ps1
```

Nginx 使用已有 Windows 安装时，由 `NGINX_SERVICE_NAME` 或 `NGINX_EXE` 配置管理。不要同时启动公网隧道作为生产入口。

## 备份策略

- 每日自动备份数据库。
- 每次发布前手动备份数据库和上传文件。
- 本地备份保留不少于 30 天。
- 必须复制到另一块磁盘、NAS 或对象存储。
- 每次备份必须有 SHA-256 和 manifest。
- 备份目录不能放在系统盘唯一位置。

## 恢复演练与真实恢复

非破坏性演练：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-restore.ps1
```

真实恢复必须在维护窗口执行，并明确目标数据库名：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\restore-backup.ps1 `
  -BackupPath C:\www\gongdan\data\backups\mysql_YYYYMMDD_HHMMSS.sql `
  -ConfirmDatabase heihu_workorder
```

真实恢复会重建数据库，执行前脚本会自动做安全备份；没有 `-ConfirmDatabase` 或名称不匹配时不会执行。

## 升级与回滚

1. 先查看版本和变更说明。
2. 执行 `verify-release.ps1`。
3. 执行 `preflight.ps1 -Mode Commercial`。
4. 备份数据库和上传文件。
5. 在维护窗口替换 `www`、`server`、`nginx` 和 `scripts`。
6. 启动服务并执行 `smoke-test.ps1 -Login`。
7. 失败时停止流量，保留故障现场，使用安全备份恢复数据库，再恢复上一稳定包。

数据库迁移采用向前兼容原则；已经执行的迁移不能只通过替换 JAR 回滚，必须结合数据库备份评估回退。

## 断电恢复

- MySQL 服务设置为自动启动。
- 安装启动恢复计划任务。
- Nginx 配置为服务或写入 `NGINX_EXE`。
- BIOS/UEFI 开启 `Restore on AC Power Loss`。
- 使用 UPS，避免数据库频繁硬断电。
- 重启后执行一次 `status.ps1` 和 `smoke-test.ps1` 并留存证据。
